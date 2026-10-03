package com.fc.v2.service.check;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fc.v2.mapper.auto.TIsCheckRowMapper;
import com.fc.v2.mapper.auto.TIsEnterpriseMapper;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.model.auto.TIsEnterprise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 执法检查记录整批导入规则单测（纯 Mockito + 内存点算，不连库）。
 *
 * @author fuce
 * @date 2026-10-03
 */
class CheckRowTxTest {

    @Mock
    private TIsCheckRowMapper checkRowMapper;
    @Mock
    private TIsEnterpriseMapper enterpriseMapper;
    private CheckRowTx tx;

    private final Date now = new Date(1_780_000_000_000L);
    private final List<TIsCheckRow> store = new ArrayList<>();
    private long idSeq = 1L;
    /** 底档里查得到的在用企业编号（退役的不放进来＝查无此家） */
    private final Set<String> knownSites = new HashSet<>(Arrays.asList("EN01", "EN02"));

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        store.clear();
        idSeq = 1L;
        when(checkRowMapper.insert(any(TIsCheckRow.class))).thenAnswer(inv -> {
            TIsCheckRow r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(idSeq++);
            }
            store.add(r);
            return 1;
        });
        when(checkRowMapper.selectList(any())).thenAnswer(inv -> selectByWrapper(inv.getArgument(0)));
        when(checkRowMapper.selectCount(any())).thenAnswer(inv -> countByWrapper(inv.getArgument(0)));
        when(enterpriseMapper.selectList(any())).thenAnswer(inv -> {
            Wrapper<?> w = inv.getArgument(0);
            // 注意：MyBatis-Plus 的 in(...) 参数是惰性的，首次 getSqlSegment() 渲染时才入参数表。
            w.getSqlSegment();
            Set<String> queried = new HashSet<>();
            for (Object v : wrapperParams(w).values()) {
                if (v instanceof Collection) {
                    for (Object o : (Collection<?>) v) {
                        if (o != null) queried.add(o.toString());
                    }
                } else if (v instanceof String) {
                    queried.add((String) v);
                }
            }
            List<TIsEnterprise> list = new ArrayList<>();
            for (String site : queried) {
                if (knownSites.contains(site)) {
                    TIsEnterprise e = new TIsEnterprise();
                    e.setSiteNo(site);
                    e.setSiteName("底档-" + site);
                    e.setStatus(0);
                    list.add(e);
                }
            }
            return list;
        });
        tx = new CheckRowTx();
        org.springframework.test.util.ReflectionTestUtils.setField(tx, "checkRowMapper", checkRowMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(tx, "enterpriseMapper", enterpriseMapper);
    }

    // ---------------- 行 DTO ----------------

    private CheckRowLine line(int rowNo, String site, String date, String hazard, String qty) {
        return new CheckRowLine(rowNo, site, date, hazard, qty);
    }

    private List<CheckRowLine> lines(CheckRowLine... ls) {
        return new ArrayList<>(Arrays.asList(ls));
    }

    // ---------------- 核心规则 ----------------

    @Test
    void eachRowValidatedIndependently_badRowsDoNotBlockOthers() {
        BatchReceipt r = tx.submitBatch("B001", lines(
                line(1, "EN01", "2026-09-01", "2", "95.5"),
                line(2, "ENX", "2026-09-01", "1", "90"),
                line(3, "EN02", "2026-09-02", "0", "-8")), "u1", now);
        assertEquals(3, r.getTotalCnt());
        assertEquals(1, r.getStoredCnt());
        assertEquals(2, r.getRejectedCnt());
        assertTrue(r.isConsistent(), "入库+退回=全批");
        assertEquals(2, r.getErrors().size());
        assertEquals(2, r.getErrors().get(0).getRowNo());
        assertEquals(CheckConst.ITEM_SITE_NOT_FOUND, r.getErrors().get(0).getItemCode());
        assertEquals(3, r.getErrors().get(1).getRowNo());
        assertEquals(CheckConst.ITEM_QTY_NEGATIVE, r.getErrors().get(1).getItemCode());
        // 好行真入库：四样认全（插入顺序退行先行、好行后插，按行号找）
        TIsCheckRow ok = byRowNo(1);
        assertEquals(CheckConst.ST_OK, ok.getStatus());
        assertEquals("EN01", ok.getSiteNo());
        assertEquals(0, new BigDecimal("95.5").compareTo(ok.getQty()));
        assertEquals(Integer.valueOf(2), ok.getHazardCnt());
        assertNotNull(ok.getCheckDate());
        // 退行只挂一个码、一句原由
        TIsCheckRow bad = byRowNo(2);
        assertEquals(CheckConst.ST_FAIL, bad.getStatus());
        assertNotNull(bad.getItemCode());
        assertNotNull(bad.getRemark());
    }

    @Test
    void scoreAndFieldRulesEachGetOwnCode() {
        checkOneBad("EN01", "2026-09-01", "1", "", CheckConst.ITEM_QTY_EMPTY);
        checkOneBad("EN01", "2026-09-01", "1", "abc", CheckConst.ITEM_QTY_FORMAT);
        checkOneBad("EN01", "2026-09-01", "1", "0", CheckConst.ITEM_QTY_NEGATIVE);
        checkOneBad("EN01", "2026-09-01", "1", "100.01", CheckConst.ITEM_QTY_TOO_LARGE);
        checkOneBad("EN01", "2026-09-01", "1", "95.123", CheckConst.ITEM_QTY_SCALE);
        checkOneBad("EN01", "2026-09-01", "", "90", CheckConst.ITEM_HAZARD_CNT_INVALID);
        checkOneBad("EN01", "2026-09-01", "101", "90", CheckConst.ITEM_HAZARD_CNT_OVER);
        checkOneBad("EN01", "2026-13-40", "1", "90", CheckConst.ITEM_CHECK_DATE);
        checkOneBad("EN01", "九月一日", "1", "90", CheckConst.ITEM_CHECK_DATE);
        checkOneBad("", "2026-09-01", "1", "90", CheckConst.ITEM_SITE_NOT_FOUND);
    }

    private void checkOneBad(String site, String date, String hazard, String qty, String code) {
        store.clear();
        BatchReceipt r = tx.submitBatch("B" + System.nanoTime(),
                lines(line(1, site, date, hazard, qty)), "u1", now);
        assertEquals(1, r.getRejectedCnt(), "字段[" + code + "]应退一行");
        assertEquals(code, r.getErrors().get(0).getItemCode());
    }

    @Test
    void duplicateBatchKeepsFirstOnly_andNeverRewritesRejectedReason() {
        tx.submitBatch("DUP", lines(
                line(1, "EN01", "2026-09-01", "1", "90"),
                line(2, "ENX", "2026-09-01", "1", "90")), "u1", now);
        assertEquals(2, store.size());
        String firstReason = byRowNo(2).getRemark();

        BatchReceipt again = tx.submitBatch("DUP", lines(
                line(1, "EN02", "2026-09-03", "5", "60")), "u1", now);
        assertTrue(again.isDuplicate());
        assertEquals(2, store.size(), "后交一行不进，库里只留头一遍");
        assertEquals(1, again.getStoredCnt());
        assertEquals(1, again.getRejectedCnt());
        assertEquals(firstReason, byRowNo(2).getRemark(), "已退行不许补第二句原因");
        verify(checkRowMapper, never()).updateById(any());
        // 头遍数据不被覆盖：行1仍是 EN01
        assertEquals("EN01", byRowNo(1).getSiteNo());
    }

    private TIsCheckRow byRowNo(int rowNo) {
        for (TIsCheckRow r : store) {
            if (r.getRowNo() != null && r.getRowNo() == rowNo) {
                return r;
            }
        }
        throw new IllegalStateException("夹具里没有第 " + rowNo + " 行");
    }

    @Test
    void overLimitWholeBatchRejectedNotOneRowStored() {
        List<CheckRowLine> big = new ArrayList<>();
        for (int i = 1; i <= CheckConst.MAX_ROWS_PER_BATCH + 1; i++) {
            big.add(line(i, "EN01", "2026-09-01", "1", "90"));
        }
        BatchReceipt r = tx.submitBatch("BIG", big, "u1", now);
        assertFalse(r.isAccepted());
        assertEquals(CheckConst.MAX_ROWS_PER_BATCH + 1, r.getTotalCnt());
        assertTrue(store.isEmpty(), "超限整批退、一行不留");
        assertTrue(r.getMessage().contains("整批退回"));
    }

    @Test
    void emptyBatchIsNotAcceptedAndNeverSaysHe() {
        BatchReceipt r = tx.submitBatch("EMPTY", lines(), "u1", now);
        assertFalse(r.isAccepted());
        assertFalse(r.getMessage().contains("已核讫"), "空批回执不许写\"已核讫\"");
        assertTrue(store.isEmpty());
    }

    @Test
    void sameEnterpriseTwoRowsMerged_scoreOnce_rowNoTakesFirst() {
        BatchReceipt r = tx.submitBatch("MERGE", lines(
                line(1, "EN01", "2026-08-15", "1", "91"),
                line(2, "ENX", "2026-08-16", "1", "90"),
                line(3, "EN01", "2026-09-15", "2", "88")), "u1", now);
        assertEquals(3, r.getTotalCnt());
        assertEquals(2, r.getStoredCnt());
        assertEquals(1, r.getRejectedCnt());
        assertEquals(1, r.getFormalCnt(), "同企业横跨两行并成一条，得分只算一次");
        TIsCheckRow first = byRowNo(1);
        TIsCheckRow second = byRowNo(3);
        assertNull(first.getMergeInto());
        assertEquals(Integer.valueOf(1), second.getMergeInto(), "并起来的行次认前头那条");
        assertNull(second.getQty(), "后头并入行不重复算分");
    }

    @Test
    void tallyMismatchRowsArePickedOut() {
        // 手工塞一条没落定（待处理）的行：三数应判不一致并挑出行号
        TIsCheckRow hanging = new TIsCheckRow();
        hanging.setBatchNo("H");
        hanging.setRowNo(7);
        hanging.setStatus(CheckConst.ST_PENDING);
        hanging.setDelFlag(0);
        store.add(hanging);
        BatchReceipt r = tx.submitBatch("H", lines(
                line(1, "EN01", "2026-09-01", "1", "90")), "u1", now);
        assertFalse(r.isConsistent());
        assertTrue(r.getMismatchRows().contains(7));
    }

    @Test
    void statusIsSystemSetNeverTakenFromCaller() {
        tx.submitBatch("S", lines(line(1, "EN01", "2026-09-01", "1", "90")), "u1", now);
        assertEquals(CheckConst.ST_OK, store.get(0).getStatus());
    }

    // ---------------- 原处理方法签名不动 ----------------

    @Test
    void legacySubmitBatchSignatureUnchanged() {
        TIsCheckRow a = entityRow("L1", 1, "OK1", new BigDecimal("90"));
        TIsCheckRow b = entityRow("L1", 2, null, null); // 非法：无类别码、无分
        int ok = tx.submitBatch("L1", Arrays.asList(a, b));
        assertEquals(1, ok);
        assertEquals(CheckConst.ST_OK, store.get(0).getStatus());
        assertEquals(CheckConst.ST_FAIL, store.get(1).getStatus());

        // 空批 0、超限 -1、重传返回头遍入库数
        assertEquals(0, tx.submitBatch("L2", new ArrayList<>()));
        assertEquals(1, tx.submitBatch("L1", Arrays.asList(entityRow("L1", 9, "OK9", new BigDecimal("80")))));
        List<TIsCheckRow> big = new ArrayList<>();
        for (int i = 0; i < CheckConst.MAX_ROWS_PER_BATCH + 1; i++) {
            big.add(entityRow("BIG", i + 1, "OK", new BigDecimal("90")));
        }
        assertEquals(-1, tx.submitBatch("BIG", big));
    }

    private TIsCheckRow entityRow(String batch, int rowNo, String item, BigDecimal qty) {
        TIsCheckRow r = new TIsCheckRow();
        r.setBatchNo(batch);
        r.setRowNo(rowNo);
        r.setItemCode(item);
        r.setQty(qty);
        return r;
    }

    // ---------------- 文件读取：编码、表头、xlsx ----------------

    @Test
    void gbkCsvGivesPlainTranscodeHintNotStack() {
        byte[] gbk = ("企业编号,检查日期,隐患条数,得分\nEN01,2026-09-01,1,90\n")
                .getBytes(Charset.forName("GBK"));
        CheckImportException ex = assertThrows(CheckImportException.class,
                () -> CheckFileReader.read(gbk, "q.csv"));
        assertTrue(ex.getMessage().contains("UTF-8"));
        assertTrue(ex.getMessage().contains("GBK"));
    }

    @Test
    void utf8CsvWithBomParsesAndRowNumbersStartAtOne() {
        byte[] body = ("企业编号,检查日期,隐患条数,得分\n"
                + "EN01,2026-09-01,2,95.5\n"
                + "EN02,2026/9/2,0,88\n").getBytes(Charset.forName("UTF-8"));
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] csv = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, csv, 0, bom.length);
        System.arraycopy(body, 0, csv, bom.length, body.length);
        List<CheckRowLine> lines = CheckFileReader.read(csv, "q.csv");
        assertEquals(2, lines.size());
        assertEquals(1, lines.get(0).getRowNo());
        assertEquals("EN01", lines.get(0).getSiteNo());
        assertEquals("95.5", lines.get(0).getQtyRaw());
        assertEquals(2, lines.get(1).getRowNo());
    }

    @Test
    void headerColumnsOrderFreeAndQuotedCsv() {
        String csv = "得分,企业编号,检查日期,隐患条数\n\"90\",\"EN01\",\"2026-09-01\",\"1\"\n";
        List<CheckRowLine> lines = CheckFileReader.read(csv.getBytes(Charset.forName("UTF-8")), "q.csv");
        assertEquals(1, lines.size());
        assertEquals("EN01", lines.get(0).getSiteNo());
        assertEquals("90", lines.get(0).getQtyRaw());
        assertEquals("1", lines.get(0).getHazardCntRaw());
    }

    @Test
    void trailingBlankLinesAndNoFinalNewlineDoNotInventRows() {
        // 结尾多打一个空行：不造幽灵行；末行没换行也收得到
        byte[] withBlank = ("企业编号,检查日期,隐患条数,得分\nEN01,2026-09-01,1,90\n\n")
                .getBytes(Charset.forName("UTF-8"));
        assertEquals(1, CheckFileReader.read(withBlank, "q.csv").size());
        byte[] noFinalNl = ("企业编号,检查日期,隐患条数,得分\nEN01,2026-09-01,1,90")
                .getBytes(Charset.forName("UTF-8"));
        assertEquals(1, CheckFileReader.read(noFinalNl, "q.csv").size());
        // 四列全空的物理空行（,,,）仍是一行数据，进门要退回而不是吞掉
        byte[] emptyCells = ("企业编号,检查日期,隐患条数,得分\n,,,\n")
                .getBytes(Charset.forName("UTF-8"));
        assertEquals(1, CheckFileReader.read(emptyCells, "q.csv").size());
    }

    @Test
    void missingHeaderIsRejectedPlainly() {
        byte[] csv = "编号,日期,条数,分\nEN01,2026-09-01,1,90\n".getBytes(Charset.forName("UTF-8"));
        CheckImportException ex = assertThrows(CheckImportException.class,
                () -> CheckFileReader.read(csv, "q.csv"));
        assertTrue(ex.getMessage().contains("表头"));
    }

    @Test
    void xlsxZipXmlParsesWithoutPoi() throws Exception {
        byte[] xlsx = minimalXlsx();
        List<CheckRowLine> lines = CheckFileReader.read(xlsx, "q.xlsx");
        assertEquals(2, lines.size());
        assertEquals("EN01", lines.get(0).getSiteNo());
        assertEquals("2026-09-01", lines.get(0).getCheckDateRaw());
        assertEquals("95.5", lines.get(0).getQtyRaw());
        assertEquals("EN02", lines.get(1).getSiteNo());
    }

    @Test
    void ole2OldXlsGivesSaveAsHint() {
        byte[] xls = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0, 0, 0, 0};
        CheckImportException ex = assertThrows(CheckImportException.class,
                () -> CheckFileReader.read(xls, "old.xls"));
        assertTrue(ex.getMessage().contains("另存为"));
    }

    /** 手搓最小 xlsx（zip：sharedStrings + sheet1），验 JDK 原生解析这条路。 */
    private byte[] minimalXlsx() throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zos.write(("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" "
                    + "count=\"6\" uniqueCount=\"6\"><si><t>企业编号</t></si><si><t>检查日期</t></si>"
                    + "<si><t>隐患条数</t></si><si><t>得分</t></si>"
                    + "<si><t>EN01</t></si><si><t>EN02</t></si></sst>").getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                    + "<sheetData>"
                    + "<row r=\"1\">"
                    + "<c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c>"
                    + "<c r=\"C1\" t=\"s\"><v>2</v></c><c r=\"D1\" t=\"s\"><v>3</v></c></row>"
                    + "<row r=\"2\">"
                    + "<c r=\"A2\" t=\"s\"><v>4</v></c>"
                    + "<c r=\"B2\" t=\"str\"><v>2026-09-01</v></c>"
                    + "<c r=\"C2\"><v>2</v></c><c r=\"D2\"><v>95.5</v></c></row>"
                    + "<row r=\"3\">"
                    + "<c r=\"A3\" t=\"s\"><v>5</v></c>"
                    + "<c r=\"B3\" t=\"str\"><v>2026-09-02</v></c>"
                    + "<c r=\"C3\"><v>0</v></c><c r=\"D3\"><v>88</v></c></row>"
                    + "</sheetData></worksheet>";
            zos.write(sheet.getBytes("UTF-8"));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    // ---------------- 内存点算：按 QueryWrapper 条件筛行 ----------------

    @SuppressWarnings("unchecked")
    private List<TIsCheckRow> selectByWrapper(Wrapper<?> w) {
        List<TIsCheckRow> hit = new ArrayList<>();
        for (TIsCheckRow r : store) {
            if (matches(r, w)) {
                hit.add(r);
            }
        }
        hit.sort((a, b) -> {
            int ra = a.getRowNo() == null ? Integer.MAX_VALUE : a.getRowNo();
            int rb = b.getRowNo() == null ? Integer.MAX_VALUE : b.getRowNo();
            return Integer.compare(ra, rb);
        });
        return new ArrayList<>(hit);
    }

    private Integer countByWrapper(Wrapper<?> w) {
        return selectByWrapper(w).size();
    }

    /** 只解析本类用到的原子条件：col = ? / col IN (...) / col IS NULL（ORDER BY 忽略）。 */
    private boolean matches(TIsCheckRow row, Wrapper<?> w) {
        String sql = w.getSqlSegment() == null ? "" : w.getSqlSegment();
        int orderAt = sql.indexOf("ORDER BY");
        if (orderAt >= 0) {
            sql = sql.substring(0, orderAt);
        }
        sql = sql.trim();
        if (sql.startsWith("(") && sql.endsWith(")")) {
            sql = sql.substring(1, sql.length() - 1);
        }
        if (sql.isEmpty()) {
            return true;
        }
        Map<String, Object> params = wrapperParams(w);
        for (String atomRaw : splitAnd(sql)) {
            String atom = atomRaw.trim();
            String col;
            Object expect;
            if (atom.endsWith("IS NULL")) {
                col = atom.substring(0, atom.length() - "IS NULL".length()).trim();
                if (valueOf(row, col) != null) {
                    return false;
                }
                continue;
            }
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("#\\{ew\\.paramNameValuePairs\\.([A-Za-z0-9_]+)\\}").matcher(atom);
            List<String> keys = new ArrayList<>();
            while (m.find()) {
                keys.add(m.group(1));
            }
            if (atom.contains(" IN ")) {
                col = atom.substring(0, atom.indexOf(" IN ")).trim();
                Set<Object> want = new HashSet<>();
                for (String k : keys) {
                    want.add(params.get(k));
                }
                if (!want.contains(valueOf(row, col))) {
                    return false;
                }
            } else if (!keys.isEmpty()) {
                col = atom.substring(0, atom.indexOf('=')).trim();
                expect = params.get(keys.get(0));
                Object got = valueOf(row, col);
                if (got == null || !got.equals(expect)) {
                    return false;
                }
            }
        }
        return true;
    }

    private List<String> splitAnd(String sql) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (depth == 0 && c == 'A' && sql.startsWith("AND", i)
                    && (i == 0 || sql.charAt(i - 1) == ' ')
                    && (i + 3 >= sql.length() || sql.charAt(i + 3) == ' ')) {
                out.add(cur.toString());
                cur.setLength(0);
                i += 2;
                continue;
            }
            cur.append(c);
        }
        if (cur.toString().trim().length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    private Object valueOf(TIsCheckRow row, String col) {
        switch (col) {
            case "batch_no": return row.getBatchNo();
            case "status": return row.getStatus();
            case "del_flag": return row.getDelFlag();
            case "merge_into": return row.getMergeInto();
            case "site_no": return row.getSiteNo();
            case "check_date": return row.getCheckDate();
            default: return null;
        }
    }

    private Map<String, Object> wrapperParams(Wrapper<?> w) {
        if (w instanceof com.baomidou.mybatisplus.core.conditions.AbstractWrapper) {
            return new LinkedHashMap<>(
                    ((com.baomidou.mybatisplus.core.conditions.AbstractWrapper<?, ?, ?>) w)
                            .getParamNameValuePairs());
        }
        return new LinkedHashMap<>();
    }
}
