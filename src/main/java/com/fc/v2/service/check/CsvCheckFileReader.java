package com.fc.v2.service.check;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 检查记录整批文件读入器（纯 JDK，不引第三方包）。
 *
 * 口径：
 * 1. 只吃 UTF-8 的 CSV（Excel"另存为 CSV UTF-8（逗号分隔）"即是）；开头带 UTF-8 BOM 的照吃，剥掉 BOM。
 * 2. 按 UTF-8 严格解码（非法字节直接报，不靠替换字符蒙混）；解不开且字节像 GBK 的，给一句明确的转码提示，
 *    绝不让乱码或堆栈流到界面上。
 * 3. 表头行不算数：首行看着像表头（含"企业编号/检查时间/隐患/得分"字样）就跳过，第一条数据行号为 1；
 *    没表头的文件则逐行从 1 起。行次进门这一刻按原文件先后定死，后面怎么验都不许重排。
 * 4. 支持引号包裹、引号内逗号/换行、双引号转义；行尾 \r\n \n \r 都认。
 *
 * @author fuce
 * @date 2026-10-03
 */
public final class CsvCheckFileReader {

    /** 表头四列固定次序：企业编号、检查时间、隐患条数、本行得分。 */
    public static final int COL_SITE_NO = 0;
    public static final int COL_CHECK_TIME = 1;
    public static final int COL_HAZARD_CNT = 2;
    public static final int COL_QTY = 3;
    public static final int COL_COUNT = 4;

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private CsvCheckFileReader() {
    }

    /**
     * 读入整份文件，按原文件先后给出数据行（行号已填好，表头不在其中）。
     *
     * @param bytes    上传文件原始字节
     * @param fileName 原文件名（只用于话术与后缀判断）
     * @throws CheckFileException 编码/后缀/空内容不对时，message 即给用户看的明确提示
     */
    public static List<CheckImportRow> parse(byte[] bytes, String fileName) {
        if (bytes == null || bytes.length == 0) {
            // 空文件按"空批"进门：由逐行处理方法出 0/0/0 回执，这里不拦。
            return new ArrayList<>();
        }
        if (fileName != null) {
            String lower = fileName.toLowerCase();
            if (!lower.endsWith(".csv") && !lower.endsWith(".txt")) {
                throw new CheckFileException("只支持 UTF-8 编码的 CSV 文件：请在 Excel 里点"
                        + "“另存为”，类型选“CSV UTF-8（逗号分隔）(*.csv)”后重新上传，原文件：" + fileName);
            }
        }

        int from = 0;
        if (bytes.length >= 3 && bytes[0] == UTF8_BOM[0] && bytes[1] == UTF8_BOM[1] && bytes[2] == UTF8_BOM[2]) {
            from = 3;
        } else if (looksLikeUtf16Bom(bytes)) {
            throw new CheckFileException("文件是 UTF-16 编码，系统只收 UTF-8：请另存为"
                    + "“CSV UTF-8（逗号分隔）”后重新上传");
        }

        String text = decodeUtf8(bytes, from, fileName);
        List<List<String>> records = splitCsv(text);

        int start = 0;
        // 容忍开头夹着空白行：跳过它们找表头；找到就从表头后一行起算。
        int probe = 0;
        while (probe < records.size()) {
            List<String> cells = records.get(probe);
            boolean physicalBlank = cells.size() <= 1
                    && (cells.isEmpty() || cells.get(0).trim().isEmpty());
            if (!physicalBlank) {
                break;
            }
            probe++;
        }
        if (probe < records.size() && looksLikeHeader(join(records.get(probe)))) {
            start = probe + 1;
        }

        List<CheckImportRow> rows = new ArrayList<>();
        int rowNo = 0;
        for (int i = start; i < records.size(); i++) {
            List<String> cells = records.get(i);
            // 真空物理行（就一个空列、半个逗号都没有）不占行号；
            // 但 ",,," 这种带着列结构的整行空白是一条数据行，行号照编，交校验按整行空白退，不许抽走低号。
            boolean physicalBlank = cells.size() <= 1
                    && (cells.isEmpty() || cells.get(0).trim().isEmpty());
            if (physicalBlank) {
                continue;
            }
            rowNo++;
            CheckImportRow row = new CheckImportRow();
            row.setRowNo(rowNo);
            row.setSiteNo(cell(cells, COL_SITE_NO));
            row.setCheckTimeText(cell(cells, COL_CHECK_TIME));
            row.setHazardCntText(cell(cells, COL_HAZARD_CNT));
            row.setQtyText(cell(cells, COL_QTY));
            rows.add(row);
        }
        return rows;
    }

    private static String decodeUtf8(byte[] bytes, int from, String fileName) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes, from, bytes.length - from)).toString();
        } catch (CharacterCodingException e) {
            // UTF-8 解不开：基层队所老表多是 Excel 默认存出的 GBK。给明确转码提示，不抛乱码堆栈。
            throw new CheckFileException("文件不是 UTF-8 编码（内容像是 GBK/ANSI 存的，中文会乱码）："
                    + "请用记事本打开后“另存为”，底部编码选“UTF-8”保存；或在 Excel 里选"
                    + "“CSV UTF-8（逗号分隔）”另存后重新上传"
                    + (fileName == null ? "" : "（原文件：" + fileName + "）"), e);
        } catch (IllegalArgumentException e) {
            // UTF-16 等多字节序列有时在剥 BOM 判断后仍会落到这里。
            throw new CheckFileException("文件编码无法按 UTF-8 读入，请另存为“CSV UTF-8（逗号分隔）”后重新上传", e);
        }
    }

    private static boolean looksLikeUtf16Bom(byte[] b) {
        return (b[0] == (byte) 0xFF && b[1] == (byte) 0xFE)
                || (b[0] == (byte) 0xFE && b[1] == (byte) 0xFF);
    }

    private static boolean looksLikeHeader(String joined) {
        return joined.contains("企业编号") || joined.contains("检查时间")
                || joined.contains("隐患") || joined.contains("得分");
    }

    private static String cell(List<String> cells, int idx) {
        return idx < cells.size() ? cells.get(idx).trim() : "";
    }

    private static String join(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (String c : cells) {
            sb.append(c);
        }
        return sb.toString();
    }

    /**
     * 按 CSV 规矩切记录：逗号分列、双引号包裹、"" 转义成一个 "、引号内允许换行。
     * 物理上的空行返回成一条单空列记录（由调用方按空白跳过，不占行号）。
     */
    static List<List<String>> splitCsv(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        boolean rowHasContent = false;

        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
                continue;
            }
            if (c == '"') {
                inQuotes = true;
                fieldStarted = true;
                rowHasContent = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                rowHasContent = true;
            } else if (c == '\n') {
                flushRecord(records, fields, field, fieldStarted, rowHasContent);
                field.setLength(0);
                fields = new ArrayList<>();
                inQuotes = false;
                fieldStarted = false;
                rowHasContent = false;
            } else if (c == '\r') {
                // \r\n 一起；单独 \r 也算行尾。
                flushRecord(records, fields, field, fieldStarted, rowHasContent);
                field.setLength(0);
                fields = new ArrayList<>();
                inQuotes = false;
                fieldStarted = false;
                rowHasContent = false;
                if (i + 1 < len && text.charAt(i + 1) == '\n') {
                    i++;
                }
            } else {
                field.append(c);
                if (!Character.isWhitespace(c)) {
                    rowHasContent = true;
                }
                fieldStarted = true;
            }
        }
        // 文件末尾最后一行（可能没有换行收尾）。
        if (fieldStarted || !fields.isEmpty() || rowHasContent) {
            fields.add(field.toString());
            records.add(fields);
        }
        return records;
    }

    private static void flushRecord(List<List<String>> records, List<String> fields,
                                    StringBuilder field, boolean fieldStarted, boolean rowHasContent) {
        if (fieldStarted || !fields.isEmpty()) {
            fields.add(field.toString());
        }
        // 空物理行也保留成一条空记录，交调用方判空白；这样语义只有一处。
        if (!fields.isEmpty() || rowHasContent) {
            records.add(fields);
        } else {
            List<String> empty = new ArrayList<>();
            empty.add("");
            records.add(empty);
        }
    }
}
