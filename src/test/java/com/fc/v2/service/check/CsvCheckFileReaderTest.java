package com.fc.v2.service.check;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 整批文件读入器单测：UTF-8/BOM、GBK 明确转码提示、表头不算、行号按原文件先后、引号转义。
 *
 * @author fuce
 * @date 2026-10-03
 */
class CsvCheckFileReaderTest {

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void headerRowDoesNotCountFirstDataRowIsOne() {
        String csv = "企业编号,检查时间,隐患条数,本行得分\r\n"
                + "EN00,2026-09-30,3,88.5\r\n"
                + "EN01,2026-09-01 14:30,0,90\r\n";
        List<CheckImportRow> rows = CsvCheckFileReader.parse(utf8(csv), "q3.csv");
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).getRowNo());
        assertEquals("EN00", rows.get(0).getSiteNo());
        assertEquals("88.5", rows.get(0).getQtyText());
        assertEquals(2, rows.get(1).getRowNo());
        assertEquals("2026-09-01 14:30", rows.get(1).getCheckTimeText());
    }

    @Test
    void noHeaderFileAlsoStartsAtOne() {
        String csv = "EN00,2026-09-30,1,80\nEN01,2026-10-01,2,70\n";
        List<CheckImportRow> rows = CsvCheckFileReader.parse(utf8(csv), "nohead.txt");
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).getRowNo());
        assertEquals(2, rows.get(1).getRowNo());
    }

    @Test
    void utf8BomIsStripped() {
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = utf8("企业编号,检查时间,隐患条数,得分\nEN00,2026-09-30,1,80\n");
        byte[] all = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, all, 0, bom.length);
        System.arraycopy(body, 0, all, bom.length, body.length);
        List<CheckImportRow> rows = CsvCheckFileReader.parse(all, "bom.csv");
        assertEquals(1, rows.size());
        assertEquals("EN00", rows.get(0).getSiteNo());
    }

    @Test
    void gbkBytesGetAHumanTranscodeHintNotAStackTrace() {
        byte[] gbk = "企业编号,检查时间,隐患条数,得分\nEN00,2026-09-30,1,80\n"
                .getBytes(Charset.forName("GBK"));
        CheckFileException ex = assertThrows(CheckFileException.class,
                () -> CsvCheckFileReader.parse(gbk, "laoge.csv"));
        String msg = ex.getMessage();
        assertTrue(msg.contains("UTF-8"), "提示必须指明要转成 UTF-8：" + msg);
        assertTrue(msg.contains("GBK") || msg.contains("ANSI"), "提示必须点破 GBK/ANSI 来由：" + msg);
        assertTrue(msg.contains("另存为"), "提示必须给出具体转码动作：" + msg);
        assertTrue(msg.contains("laoge.csv"), "提示带上原文件名方便对照：" + msg);
    }

    @Test
    void nonCsvExtensionRefusedPlainly() {
        CheckFileException ex = assertThrows(CheckFileException.class,
                () -> CsvCheckFileReader.parse(utf8("x"), "台账.xlsx"));
        assertTrue(ex.getMessage().contains("CSV UTF-8"));
    }

    @Test
    void quotedCommaNewlineAndDoubledQuoteAreOneField() {
        String csv = "企业编号,检查时间,隐患条数,本行得分\r\n"
                + "\"EN,00\",\"2026-09-30\n14:30\",1,\"88\"\"5\"\r\n";
        List<CheckImportRow> rows = CsvCheckFileReader.parse(utf8(csv), "q.csv");
        assertEquals(1, rows.size());
        assertEquals("EN,00", rows.get(0).getSiteNo());
        assertEquals("2026-09-30\n14:30", rows.get(0).getCheckTimeText());
        assertEquals("88\"5", rows.get(0).getQtyText());
    }

    @Test
    void trulyBlankPhysicalLinesDoNotConsumeRowNumbers() {
        // 物理空行（换行之间没字）是导表残影，不占行号；
        // 但 ",,," 这种带列结构的整行空白是数据行，照样编号，交校验按 ROW_EMPTY 退。
        String csv = "企业编号,检查时间,隐患条数,得分\n"
                + "\n"
                + "EN00,2026-09-30,1,80\n"
                + "\n"
                + ",,,\n"
                + "EN01,2026-10-01,0,90\n";
        List<CheckImportRow> rows = CsvCheckFileReader.parse(utf8(csv), "gaps.csv");
        assertEquals(3, rows.size());
        assertEquals(1, rows.get(0).getRowNo());
        assertEquals(2, rows.get(1).getRowNo());
        assertTrue(rows.get(1).isBlank(), "第 2 条是带逗号的整行空白，必须保留行号并判整行空白");
        assertEquals(3, rows.get(2).getRowNo());
    }

    @Test
    void emptyFileIsEmptyBatch() {
        List<CheckImportRow> rows = CsvCheckFileReader.parse(new byte[0], "empty.csv");
        assertTrue(rows.isEmpty());
    }

    @Test
    void missingTrailingColumnsStillParsedAsRow() {
        List<CheckImportRow> rows = CsvCheckFileReader.parse(utf8("EN00,2026-09-30\n"), "short.csv");
        assertEquals(1, rows.size());
        assertEquals("EN00", rows.get(0).getSiteNo());
        assertEquals("", rows.get(0).getQtyText());
    }
}
