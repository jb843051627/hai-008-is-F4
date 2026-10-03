package com.fc.v2.service.check;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.InputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 导入表读取器：把上传原文逐行读成 {@link CheckRowLine}，不在这一层判业务对错。
 *
 * 进门规矩：
 * 1. 只收 .xlsx 与 UTF-8 编码的 CSV；老版 .xls（OLE2 复合文档）请另存后重传；
 * 2. CSV/文本若不是 UTF-8（典型就是 Windows 中文 Excel 默认的 GBK），给明确转码提示，
 *    绝不把乱码或编码堆栈甩给用户；
 * 3. 表头行不算数，数据行从 1 编号；列按表头名认（企业编号/检查日期/隐患条数/得分），列序不限。
 *
 * xlsx 不引 POI：格式本身是 zip+xml，用 JDK 自带 ZIP + StAX 即可逐行读。
 *
 * @author fuce
 * @date 2026-10-03
 */
public final class CheckFileReader {

    /** UTF-8 BOM（Windows 记事本/Excel 导出常带，进门先摘掉） */
    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] ZIP_MAGIC = new byte[]{0x50, 0x4B, 0x03, 0x04};
    private static final byte[] OLE2_MAGIC = new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    private static final String[] SITE_HEADERS = {"企业编号", "企业代号", "企业号", "site_no"};
    private static final String[] DATE_HEADERS = {"检查日期", "检查时间", "检查日"};
    private static final String[] HAZARD_HEADERS = {"隐患条数", "隐患数", "隐患条次"};
    private static final String[] QTY_HEADERS = {"得分", "检查得分", "本行得分", "分值"};

    private CheckFileReader() {
    }

    /**
     * 读整表。
     *
     * @param bytes    文件字节
     * @param filename 原文件名（只用来辨扩展名/xlsx 兜底）
     * @return 数据行（行号已按原文件先后定死，1 起）
     */
    public static List<CheckRowLine> read(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) {
            throw new CheckImportException("上传文件是空的，无内容可读，请确认后重传");
        }
        if (startsWith(bytes, OLE2_MAGIC)) {
            throw new CheckImportException("这是老版 .xls（Excel 97-2003）文件，系统不收；"
                    + "请用 Excel 打开后\"另存为\" .xlsx（或 CSV UTF-8）再传");
        }
        if (startsWith(bytes, ZIP_MAGIC) || (filename != null && filename.toLowerCase().endsWith(".xlsx"))) {
            return readXlsx(bytes);
        }
        return readCsv(bytes);
    }

    // ============================ CSV（只收 UTF-8） ============================

    static List<CheckRowLine> readCsv(byte[] raw) {
        byte[] data = raw;
        if (startsWith(data, UTF8_BOM)) {
            data = Arrays.copyOfRange(data, 3, data.length);
        }
        String text = decodeUtf8OrHint(data);
        List<String[]> rows = parseCsv(text);
        // 全空白文件：一行数据都没有，按空批交回，回执不得写"已核讫"。
        return toLines(rows);
    }

    /** UTF-8 严格解码：解不开（GBK 原文必解不开）给转码明话，不抛编码异常原文。 */
    static String decodeUtf8OrHint(byte[] data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(java.nio.ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            throw new CheckImportException("文件不是 UTF-8 编码，看着像 GBK（中文 Windows 的 Excel 默认存的就是它）。"
                    + "请用 Excel\"另存为\"→ 选\"CSV UTF-8（逗号分隔）\"，或用记事本打开\"另存为"
                    + "\"时把编码改成 UTF-8 后重传；系统不直接收 GBK 原文");
        }
    }

    /** 最小够用 CSV：逗号分隔、支持双引号包裹与 "" 转义；行内换行也按 CSV 规矩收。 */
    static List<String[]> parseCsv(String text) {
        List<String[]> rows = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cell.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cell.append(c);
                }
                continue;
            }
            if (c == '"' && !fieldStarted) {
                inQuotes = true;
                fieldStarted = true;
                continue;
            }
            if (c == ',') {
                cells.add(cell.toString());
                cell.setLength(0);
                fieldStarted = false;
                continue;
            }
            if (c == '\n') {
                flushRow(rows, cells, cell);
                fieldStarted = false;
                continue;
            }
            if (c == '\r') {
                // CRLF：吃掉 \r 等 \n；单独的 \r 也当行尾。
                if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    continue;
                }
                flushRow(rows, cells, cell);
                fieldStarted = false;
                continue;
            }
            cell.append(c);
            fieldStarted = true;
        }
        // 收尾：末行有内容（哪怕只有一个空字段，如 "\"\""）才算一行；裸的结尾换行不造幽灵行。
        if (cell.length() > 0 || fieldStarted || !cells.isEmpty()) {
            cells.add(cell.toString());
            rows.add(cells.toArray(new String[0]));
        }
        return rows;
    }

    /** 冲刷一行；整行空（典型：文件结尾换行）不造行。 */
    private static void flushRow(List<String[]> rows, List<String> cells, StringBuilder cell) {
        if (cells.isEmpty() && cell.length() == 0) {
            return;
        }
        cells.add(cell.toString());
        cell.setLength(0);
        rows.add(cells.toArray(new String[0]));
        cells.clear();
    }

    // ============================ xlsx（zip + StAX） ============================

    static List<CheckRowLine> readXlsx(byte[] bytes) {
        if (!startsWith(bytes, ZIP_MAGIC)) {
            // 扩展名是 .xlsx 但内容不是 zip：多半是把 .xls/.csv 改了个后缀。
            throw new CheckImportException("文件后缀是 .xlsx，内容却不是 xlsx 格式（可能是改后缀来的）。"
                    + "请用 Excel 打开后真正\"另存为 .xlsx\"，或另存为 CSV UTF-8 后重传");
        }
        PathHolder holder = null;
        try {
            holder = PathHolder.writeTemp(bytes);
            List<String> shared;
            Map<Integer, List<String>> sheetRows = new LinkedHashMap<>();
            try (ZipFile zip = new ZipFile(holder.path.toFile())) {
                shared = readSharedStrings(zip);
                ZipEntry sheet = locateFirstSheet(zip);
                if (sheet == null) {
                    throw new CheckImportException("xlsx 里找不到工作表（xl/worksheets/sheet1.xml），文件可能已损坏");
                }
                try (InputStream in = zip.getInputStream(sheet)) {
                    readSheet(in, shared, sheetRows);
                }
            }
            List<String[]> rows = new ArrayList<>();
            for (List<String> r : sheetRows.values()) {
                rows.add(r.toArray(new String[0]));
            }
            return toLines(rows);
        } catch (CheckImportException e) {
            throw e;
        } catch (Exception e) {
            throw new CheckImportException("xlsx 解析失败：" + e.getClass().getSimpleName()
                    + "。请确认文件能在 Excel 正常打开后另存一次再传");
        } finally {
            if (holder != null) {
                holder.tryDelete();
            }
        }
    }

    private static List<String> readSharedStrings(ZipFile zip) throws Exception {
        List<String> shared = new ArrayList<>();
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) {
            return shared;
        }
        XMLStreamReader xr = null;
        try (InputStream in = zip.getInputStream(entry)) {
            XMLInputFactory factory = safeXmlFactory();
            xr = factory.createXMLStreamReader(in, "UTF-8");
            StringBuilder si = null;
            while (xr.hasNext()) {
                int event = xr.next();
                if (event == XMLStreamConstants.START_ELEMENT && "si".equals(xr.getLocalName())) {
                    si = new StringBuilder();
                } else if (event == XMLStreamConstants.CHARACTERS && si != null) {
                    si.append(xr.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT && "si".equals(xr.getLocalName()) && si != null) {
                    shared.add(si.toString());
                    si = null;
                }
            }
        } finally {
            if (xr != null) {
                xr.close();
            }
        }
        return shared;
    }

    private static ZipEntry locateFirstSheet(ZipFile zip) {
        // 常规导出都叫 sheet1.xml；个别工具不按序，退化为取 worksheets 下第一个。
        ZipEntry first = zip.getEntry("xl/worksheets/sheet1.xml");
        if (first != null) {
            return first;
        }
        Enumeration<? extends ZipEntry> en = zip.entries();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (n.startsWith("xl/worksheets/") && n.endsWith(".xml")) {
                return e;
            }
        }
        return null;
    }

    private static void readSheet(InputStream sheetIn, List<String> shared, Map<Integer, List<String>> out)
            throws Exception {
        XMLStreamReader xr = null;
        try {
            XMLInputFactory factory = safeXmlFactory();
            xr = factory.createXMLStreamReader(sheetIn, "UTF-8");
            int curRow = -1;
            int curCol = -1;
            String curType = null;
            String vText = null; // <v>
            String isText = null; // <is><t>（inlineStr）
            boolean inV = false;
            boolean inIsT = false;
            int maxCol = 0;
            while (xr.hasNext()) {
                int event = xr.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = xr.getLocalName();
                    if ("row".equals(name)) {
                        curRow = parseIntOrMinus(xr.getAttributeValue(null, "r"));
                        maxCol = 0;
                    } else if ("c".equals(name)) {
                        String ref = xr.getAttributeValue(null, "r");
                        curCol = ref == null ? maxCol : columnIndex(ref);
                        maxCol = Math.max(maxCol, curCol + 1);
                        curType = xr.getAttributeValue(null, "t");
                        vText = null;
                        isText = null;
                    } else if ("v".equals(name)) {
                        inV = true;
                        vText = "";
                    } else if ("t".equals(name) && "inlineStr".equals(curType)) {
                        inIsT = true;
                        isText = "";
                    } else if (inV) {
                        // 字符事件另算；这里只占位
                    }
                } else if (event == XMLStreamConstants.CHARACTERS) {
                    if (inV) {
                        vText += xr.getText();
                    } else if (inIsT) {
                        isText += xr.getText();
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = xr.getLocalName();
                    if ("v".equals(name)) {
                        inV = false;
                    } else if ("t".equals(name)) {
                        inIsT = false;
                    } else if ("c".equals(name) && curRow >= 0) {
                        out.computeIfAbsent(curRow, k -> new ArrayList<>());
                        String value = cellValue(curType, vText, isText, shared);
                        putCell(out.get(curRow), curCol, value);
                        curCol = -1;
                        curType = null;
                    } else if ("row".equals(name)) {
                        curRow = -1;
                    }
                }
            }
        } finally {
            if (xr != null) {
                xr.close();
            }
        }
    }

    private static String cellValue(String type, String v, String inline, List<String> shared) {
        if ("s".equals(type)) {
            if (v == null || v.isEmpty()) {
                return "";
            }
            int idx = parseIntOrMinus(v.trim());
            return idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
        }
        if ("inlineStr".equals(type)) {
            return inline == null ? "" : inline;
        }
        if ("str".equals(type)) {
            return v == null ? "" : v;
        }
        return v == null ? "" : v;
    }

    private static void putCell(List<String> row, int col, String value) {
        while (row.size() <= col) {
            row.add("");
        }
        row.set(col, value == null ? "" : value.trim());
    }

    /** A1/B12 → 0 基列号 */
    static int columnIndex(String cellRef) {
        int col = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char ch = cellRef.charAt(i);
            if (ch >= 'A' && ch <= 'Z') {
                col = col * 26 + (ch - 'A' + 1);
            } else if (ch >= 'a' && ch <= 'z') {
                col = col * 26 + (ch - 'a' + 1);
            } else {
                break;
            }
        }
        return col - 1;
    }

    private static int parseIntOrMinus(String s) {
        if (s == null) {
            return -1;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static XMLInputFactory safeXmlFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // 外部表/实体一律不解析，防 XXE。
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        return factory;
    }

    // ============================ 表头认列、行号定死 ============================

    private static List<CheckRowLine> toLines(List<String[]> rows) {
        // 找到表头行（默认第一行；容忍 Excel 偶发的空首行）。
        int headerIdx = -1;
        int[] cols = null;
        for (int i = 0; i < rows.size() && i < 5; i++) {
            int[] found = locateColumns(rows.get(i));
            if (found != null) {
                headerIdx = i;
                cols = found;
                break;
            }
        }
        if (cols == null) {
            throw new CheckImportException("表头认不全：表头必须含\"企业编号、检查日期、隐患条数、得分\"四列，"
                    + "列序不限；请按模板表头核对第一行");
        }
        List<CheckRowLine> lines = new ArrayList<>();
        int rowNo = 0;
        for (int i = headerIdx + 1; i < rows.size(); i++) {
            String[] r = rows.get(i);
            String site = cell(r, cols[0]);
            String date = cell(r, cols[1]);
            String hazard = cell(r, cols[2]);
            String qty = cell(r, cols[3]);
            // 物理上存在的数据行哪怕全空也算一行：空行不能当作"过了"，要退回照号定位。
            rowNo++;
            lines.add(new CheckRowLine(rowNo, site, date, hazard, qty));
        }
        return lines;
    }

    /** 返回 {企业编号列, 检查日期列, 隐患条数列, 得分列}；认不全返回 null。 */
    static int[] locateColumns(String[] header) {
        if (header == null) {
            return null;
        }
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String h = header[i] == null ? "" : header[i].trim();
            if (!h.isEmpty() && !idx.containsKey(h)) {
                idx.put(h, i);
            }
        }
        Integer site = firstPresent(idx, SITE_HEADERS);
        Integer date = firstPresent(idx, DATE_HEADERS);
        Integer hazard = firstPresent(idx, HAZARD_HEADERS);
        Integer qty = firstPresent(idx, QTY_HEADERS);
        if (site == null || date == null || hazard == null || qty == null) {
            return null;
        }
        return new int[]{site, date, hazard, qty};
    }

    private static Integer firstPresent(Map<String, Integer> idx, String[] names) {
        for (String n : names) {
            Integer i = idx.get(n);
            if (i != null) {
                return i;
            }
        }
        return null;
    }

    private static String cell(String[] row, int col) {
        if (col < 0 || col >= row.length || row[col] == null) {
            return "";
        }
        return row[col].trim();
    }

    /**
     * Excel 序列日（xlsx 日期常以数字存）转 Date：1899-12-30 起算（含 1900 闰年兼容口径）。
     * 取不到合法整数日返回 null，交由日期格式校验退回。
     */
    static Date excelSerialDate(String raw) {
        try {
            double serial = Double.parseDouble(raw.trim());
            if (serial < 1 || serial > 2958465) { // 到 9999-12-31
                return null;
            }
            long days = (long) serial;
            LocalDate ld = LocalDate.of(1899, 12, 30).plusDays(days);
            return Date.from(ld.atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean startsWith(byte[] bytes, byte[] magic) {
        if (bytes.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (bytes[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    /** 临时文件句柄（ZipFile 只吃 File，吃完即删） */
    private static final class PathHolder {
        private final java.nio.file.Path path;

        private PathHolder(java.nio.file.Path path) {
            this.path = path;
        }

        static PathHolder writeTemp(byte[] bytes) throws java.io.IOException {
            java.nio.file.Path p = java.nio.file.Files.createTempFile("check-import-", ".xlsx");
            java.nio.file.Files.write(p, bytes);
            p.toFile().deleteOnExit();
            return new PathHolder(p);
        }

        void tryDelete() {
            try {
                java.nio.file.Files.deleteIfExists(path);
            } catch (Exception ignore) {
                // 临时文件删不掉不影响导入结果，deleteOnExit 兜底。
            }
        }
    }
}
