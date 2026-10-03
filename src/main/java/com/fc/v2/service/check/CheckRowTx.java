package com.fc.v2.service.check;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TIsCheckRowMapper;
import com.fc.v2.mapper.auto.TIsEnterpriseMapper;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.model.auto.TIsEnterprise;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 执法检查记录整批导入的事务执行器（仿 RectApplyTx：单开 Bean，让 {@link Transactional} 经代理生效）。
 *
 * 一批收不收、哪行进哪行退，全由本类这个逐行处理方法说了算；错误清单行次、回执三个数，
 * 都以它给的回执为准——页面既不加行、也不动旧批。
 *
 * 老规矩今年作罢的几条：
 * - 一行有毛病只退本行（挂类别码+一句原由），同批别的行照常进，不为一两行卡掉整批；
 * - 同一批次号二次上传，库里只留头一遍，后交不覆盖，已退行不补第二句原因；得分要改另传一批；
 * - 同企业跨两行并成一条，得分只算一次，行次认前头那条；
 * - 超单批上限整批退、一行不留；空批回执不写"已核讫"。
 *
 * @author fuce
 * @date 2026-10-03
 */
@Component
public class CheckRowTx {

    /** 得分数字骨架：整数或任意位小数（小数位多少另判） */
    private static final Pattern QTY_SHAPE = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern DATE_DASH = Pattern.compile("\\d{4}-\\d{1,2}-\\d{1,2}");
    private static final Pattern DATE_SLASH = Pattern.compile("\\d{4}/\\d{1,2}/\\d{1,2}");
    private static final Pattern DATE_COMPACT = Pattern.compile("\\d{8}");

    /** 同批次号进门锁：两个中队同期送、或同号重传，一批一批办，后到者看库说话 */
    private final ConcurrentHashMap<String, Object> batchLocks = new ConcurrentHashMap<>();

    @Resource
    private TIsCheckRowMapper checkRowMapper;
    @Resource
    private TIsEnterpriseMapper enterpriseMapper;

    /**
     * 【原处理方法，签名不动】整批提交实体行：逐行校验，合法入库、非法记失败明细（保留原始行号），
     * 返回成功行数；重复批次不重入（返回头遍入库数）；超单批上限返回 -1 且整批不入库；空批返回 0。
     */
    @Transactional(rollbackFor = Exception.class)
    public int submitBatch(String batchNo, List<TIsCheckRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return 0;
        }
        if (rows.size() > CheckConst.MAX_ROWS_PER_BATCH) {
            return -1;
        }
        String no = batchNo == null || batchNo.trim().isEmpty() ? rows.get(0).getBatchNo() : batchNo.trim();
        if (no == null || no.trim().isEmpty()) {
            throw new IllegalArgumentException("批次号不能为空：一批进门先登记批次号统住全批");
        }
        // 持锁由 Service 在事务外负责（锁要罩住事务提交），这里只做幂等判定。
        long existed = countRows(no, null);
        if (existed > 0) {
            // 同号二次提交：库里只留头一遍，后交不覆盖，返回头遍入库行数。
            return (int) countRows(no, CheckConst.ST_OK);
        }
        int ok = 0;
        int seq = 0;
        for (TIsCheckRow r : rows) {
            seq++;
            r.setBatchNo(no);
            if (r.getRowNo() == null) {
                r.setRowNo(seq);
            }
            r.setDelFlag(0);
            boolean valid = r.getItemCode() != null && !r.getItemCode().trim().isEmpty()
                    && r.getQty() != null && r.getQty().compareTo(BigDecimal.ZERO) > 0;
            if (valid) {
                r.setStatus(CheckConst.ST_OK);
                ok++;
            } else {
                r.setStatus(CheckConst.ST_FAIL);
                r.setRemark("数据项代号或得分不合规，本行退回");
            }
            checkRowMapper.insert(r);
        }
        return ok;
    }

    /**
     * 【同名叠加的一层：换一种递法——进门行原文】整批导入。
     * 行次已在进门时按原文件先后定死（表头不算，第一行数据=1）。
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchReceipt submitBatch(String batchNo, List<CheckRowLine> lines, String operator, Date at) {
        if (batchNo == null || batchNo.trim().isEmpty()) {
            throw new IllegalArgumentException("批次号不能为空：一批进门先登记个批次号统住全批");
        }
        BatchReceipt receipt = new BatchReceipt();
        receipt.setBatchNo(batchNo);
        // 同批次号的进门锁由 Service 在事务外持有（锁要罩住本事务的提交）；这里只按库说话。
        long existed = countRows(batchNo, null);
        if (existed > 0) {
            // 同批次号二次上传：只认头一遍。错误清单、三数全从库内头一遍点算，后交一行不进、一句不改。
            return duplicateReceipt(batchNo);
        }
        if (lines == null || lines.isEmpty()) {
            // 一行空空如也的批：不许写"已核讫"。
            receipt.setAccepted(false);
            receipt.setMessage("整批一行数据都没有（只有表头），未核讫、未入库；请补数据后换批次号重传");
            return receipt;
        }
        if (lines.size() > CheckConst.MAX_ROWS_PER_BATCH) {
            // 超限整批退掉、一行不留：库里不登记任何行。
            receipt.setAccepted(false);
            receipt.setTotalCnt(lines.size());
            receipt.setMessage("本批 " + lines.size() + " 行，超过单批上限 "
                    + CheckConst.MAX_ROWS_PER_BATCH + " 行，整批退回、一行不留；请拆批后重传");
            return receipt;
        }

        Map<String, TIsEnterprise> siteBook = loadSiteBook(lines);

        // 先逐行验：每行至多一个类别码、只附一句原由；验完再并同企业，避免并出来的行盖住病因。
        List<TIsCheckRow> validated = new ArrayList<>();
        for (CheckRowLine line : lines) {
            TIsCheckRow row = newRow(batchNo, line.getRowNo(), operator, at);
            BatchReceipt.RowError error = validate(line, row, siteBook);
            if (error == null) {
                row.setStatus(CheckConst.ST_PENDING);
                validated.add(row);
            } else {
                row.setStatus(CheckConst.ST_FAIL);
                row.setItemCode(error.getItemCode());
                row.setRemark(error.getReason());
                checkRowMapper.insert(row);
            }
        }

        // 同企业横跨两行：并成一条，得分只算一次，行次认前头那条；
        // 后头行照常落 status=已入库（三数点算不漏行），但 merge_into 指前头行、qty 清空，正式记录不重复数。
        // formal 数入库后照库点算，不靠这里的计数器。
        Map<String, TIsCheckRow> firstBySite = new LinkedHashMap<>();
        for (TIsCheckRow row : validated) {
            TIsCheckRow first = firstBySite.get(row.getSiteNo());
            if (first == null) {
                firstBySite.put(row.getSiteNo(), row);
                row.setStatus(CheckConst.ST_OK);
            } else {
                row.setStatus(CheckConst.ST_OK);
                row.setMergeInto(first.getRowNo());
                row.setQty(null);
            }
            checkRowMapper.insert(row);
        }

        // 正式检查记录条数：入库行里 merge_into 为空的（前头行）才数，同企业得分只算一次。
        int formal = (int) checkRowMapper.selectCount(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0)
                .eq("status", CheckConst.ST_OK).isNull("merge_into"));
        return tally(batchNo, lines.size(), formal, false);
    }

    /** 进门登记：批次号、原表行次、待处理状态由系统落，人不手敲。 */
    private TIsCheckRow newRow(String batchNo, int rowNo, String operator, Date at) {
        TIsCheckRow row = new TIsCheckRow();
        row.setBatchNo(batchNo);
        row.setRowNo(rowNo);
        row.setStatus(CheckConst.ST_PENDING);
        row.setDelFlag(0);
        row.setCreateBy(operator);
        row.setCreateTime(at);
        return row;
    }

    /**
     * 逐行验，返回第一条（也是唯一一条）毛病；没毛病返回 null，并把认好的四样写进 row。
     * 校验次序：企业 → 日期 → 隐患条数 → 得分；每行只挂一个码、只附一句原由。
     */
    private BatchReceipt.RowError validate(CheckRowLine line, TIsCheckRow row, Map<String, TIsEnterprise> siteBook) {
        String site = line.getSiteNo() == null ? "" : line.getSiteNo().trim();
        if (site.isEmpty()) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_SITE_NOT_FOUND, "企业编号空着，底档查无此家");
        }
        TIsEnterprise ent = siteBook.get(site);
        if (ent == null) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_SITE_NOT_FOUND,
                    "企业编号[" + site + "]在企业底档里查无此家（已退役的也算查无），请核对编号");
        }
        row.setSiteNo(site);

        Date checkDate = parseDate(line.getCheckDateRaw());
        if (checkDate == null) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_CHECK_DATE,
                    "检查时间[" + line.getCheckDateRaw() + "]格式不像样，应如 2026-09-01");
        }
        row.setCheckDate(checkDate);

        String hazardRaw = line.getHazardCntRaw() == null ? "" : line.getHazardCntRaw().trim();
        Integer hazard = parseNonNegativeInt(hazardRaw);
        if (hazard == null) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_HAZARD_CNT_INVALID,
                    "隐患条数[" + hazardRaw + "]录得不对，应为不小于 0 的整数");
        }
        if (hazard > CheckConst.MAX_HAZARD_CNT) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_HAZARD_CNT_OVER,
                    "隐患条数 " + hazard + " 条，超过单次检查上限 " + CheckConst.MAX_HAZARD_CNT + " 条");
        }
        row.setHazardCnt(hazard);

        String qtyRaw = line.getQtyRaw() == null ? "" : line.getQtyRaw().trim();
        if (qtyRaw.isEmpty()) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_QTY_EMPTY, "本行得分空着没录");
        }
        // 先按规矩数字的样子卡：只收整数/小数，+.、科学计数法等写法按格式不像数退。
        if (!QTY_SHAPE.matcher(qtyRaw).matches()) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_QTY_FORMAT,
                    "得分[" + qtyRaw + "]录得不是规矩数字（只收整数或小数）");
        }
        BigDecimal qty;
        try {
            qty = new BigDecimal(qtyRaw);
        } catch (NumberFormatException e) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_QTY_FORMAT,
                    "得分[" + qtyRaw + "]录得不是数字，请照原表核对小数点位");
        }
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_QTY_NEGATIVE,
                    "得分[" + qtyRaw + "]录成了负数或 0，得分只收正数");
        }
        if (qty.stripTrailingZeros().scale() > CheckConst.SCORE_SCALE) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_QTY_SCALE,
                    "得分[" + qtyRaw + "]小数位超过两位，请照原表核对（去年错就出在小数位敲错）");
        }
        if (qty.compareTo(CheckConst.SCORE_MAX) > 0) {
            return new BatchReceipt.RowError(line.getRowNo(), CheckConst.ITEM_QTY_TOO_LARGE,
                    "得分[" + qtyRaw + "]是离谱大数，满分 " + CheckConst.SCORE_MAX + "，请核对");
        }
        row.setQty(qty);
        return null;
    }

    /** 本批涉及企业一次性查回：只认在用底档（退役=查无此家）。 */
    private Map<String, TIsEnterprise> loadSiteBook(List<CheckRowLine> lines) {
        Set<String> nos = new HashSet<>();
        for (CheckRowLine l : lines) {
            if (l.getSiteNo() != null && !l.getSiteNo().trim().isEmpty()) {
                nos.add(l.getSiteNo().trim());
            }
        }
        if (nos.isEmpty()) {
            return Collections.emptyMap();
        }
        List<TIsEnterprise> ents = enterpriseMapper.selectList(new QueryWrapper<TIsEnterprise>()
                .eq("del_flag", 0)
                .eq("status", 0)
                .in("site_no", nos));
        Map<String, TIsEnterprise> book = new LinkedHashMap<>();
        for (TIsEnterprise e : ents) {
            book.put(e.getSiteNo(), e);
        }
        return book;
    }

    private Date parseDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            SimpleDateFormat fmt;
            if (DATE_DASH.matcher(s).matches()) {
                fmt = new SimpleDateFormat("yyyy-M-d");
            } else if (DATE_SLASH.matcher(s).matches()) {
                fmt = new SimpleDateFormat("yyyy/M/d");
            } else if (DATE_COMPACT.matcher(s).matches()) {
                fmt = new SimpleDateFormat("yyyyMMdd");
            } else if (s.matches("\\d+(\\.\\d+)?")) {
                // xlsx 日期常以 Excel 序列数存放（纯数字格）。
                return CheckFileReader.excelSerialDate(s);
            } else {
                return null;
            }
            // 关宽松解析：2026-13-40 这种不许滑成下一档日期，按格式不像样退回。
            fmt.setLenient(false);
            return fmt.parse(s);
        } catch (java.text.ParseException ignore) {
            // 落到 null，按格式不像样退回。
        }
        return null;
    }

    private Integer parseNonNegativeInt(String raw) {
        if (raw == null || raw.isEmpty() || !raw.matches("\\d+")) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 回执三数以库里逐行点算为准：全批/已入库/退回后两数相加必须等于第一个；
     * 对不上（有待核行等）当场把行次挑出来，不靠界面上另摆的数。
     */
    private BatchReceipt tally(String batchNo, int doorTotal, int formalCnt, boolean duplicate) {
        BatchReceipt r = new BatchReceipt();
        r.setBatchNo(batchNo);
        r.setDuplicate(duplicate);
        List<TIsCheckRow> all = checkRowMapper.selectList(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0).orderByAsc("row_no", "id"));
        int stored = 0;
        int rejected = 0;
        List<Integer> mismatch = new ArrayList<>();
        List<BatchReceipt.RowError> errors = new ArrayList<>();
        for (TIsCheckRow row : all) {
            Integer st = row.getStatus();
            if (st != null && st == CheckConst.ST_OK) {
                stored++;
            } else if (st != null && st == CheckConst.ST_FAIL) {
                rejected++;
                errors.add(new BatchReceipt.RowError(row.getRowNo(), row.getItemCode(), row.getRemark()));
            } else {
                // 没落定成入库/退回的行：三数对不上的就是它。
                mismatch.add(row.getRowNo());
            }
        }
        int total = all.size();
        r.setTotalCnt(total);
        r.setStoredCnt(stored);
        r.setRejectedCnt(rejected);
        r.setFormalCnt(formalCnt);
        r.setErrors(errors);
        r.setMismatchRows(mismatch);
        boolean consistent = mismatch.isEmpty()
                && stored + rejected == total
                && total == doorTotal;
        r.setConsistent(consistent);
        if (duplicate) {
            r.setAccepted(true);
            r.setMessage("批次号[" + batchNo + "]已交过，库里只留头一遍，本批未覆盖任何行；"
                    + "头一遍点算：全批 " + total + " 行，入库 " + stored + " 行，退回 " + rejected + " 行");
        } else if (!consistent) {
            r.setMessage("点算对不上：库里 " + total + " 行，入库 " + stored + "、退回 " + rejected
                    + "，对不上的行次 " + Arrays.toString(mismatch.toArray()) + "，请按行次核查");
        } else if (total == 0) {
            r.setAccepted(false);
            r.setMessage("整批一行数据都没有，未核讫");
        } else {
            r.setMessage("批次[" + batchNo + "]点算（以库内逐行为准）：全批 " + total
                    + " 行，入库 " + stored + " 行，退回 " + rejected + " 行"
                    + (formalCnt == stored ? "" : "；同企业合并后正式检查记录 " + formalCnt + " 条，得分只算一次")
                    + (rejected == 0 ? "，全部入库" : "，错误行照清单行号定位"));
        }
        return r;
    }

    private BatchReceipt duplicateReceipt(String batchNo) {
        long stored = countRows(batchNo, CheckConst.ST_OK);
        long rejected = countRows(batchNo, CheckConst.ST_FAIL);
        long formal = checkRowMapper.selectCount(new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0)
                .eq("status", CheckConst.ST_OK).isNull("merge_into"));
        return tally(batchNo, (int) (stored + rejected), (int) formal, true);
    }

    private long countRows(String batchNo, Integer status) {
        QueryWrapper<TIsCheckRow> qw = new QueryWrapper<TIsCheckRow>()
                .eq("batch_no", batchNo).eq("del_flag", 0);
        if (status != null) {
            qw.eq("status", status);
        }
        return checkRowMapper.selectCount(qw);
    }

    /**
     * 批次进门锁：同批次号在整个应用内一把锁，一批一批办。
     * 调用方（Service）必须在<b>事务之外</b>持锁调用本类事务方法——锁要罩住事务提交，
     * 否则同号并发双击时，后到者可能在先来者事务提交前读到"批次不存在"而双写。
     */
    public Object lockOf(String batchNo) {
        return batchLocks.computeIfAbsent(batchNo, k -> new Object());
    }
}
