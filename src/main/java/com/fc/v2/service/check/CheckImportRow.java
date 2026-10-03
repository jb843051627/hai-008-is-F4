package com.fc.v2.service.check;

/**
 * 进门时照原表抄下的一行原始字符串：先按原文件先后定行次，再逐格验，
 * 校验是后话，行号不许因任何一格有毛病而挪动。
 *
 * 四格固定次序：企业编号、检查时间、隐患条数、本行得分（表头那行不算数）。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class CheckImportRow {

    private int rowNo;
    private String siteNo;
    private String checkTimeText;
    private String hazardCntText;
    private String qtyText;

    public CheckImportRow() {
    }

    public CheckImportRow(int rowNo, String siteNo, String checkTimeText,
                          String hazardCntText, String qtyText) {
        this.rowNo = rowNo;
        this.siteNo = siteNo;
        this.checkTimeText = checkTimeText;
        this.hazardCntText = hazardCntText;
        this.qtyText = qtyText;
    }

    /** 四格全空（含只有分隔符/空白的行）算整行空白。 */
    public boolean isBlank() {
        return isBlank(siteNo) && isBlank(checkTimeText) && isBlank(hazardCntText) && isBlank(qtyText);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    public int getRowNo() {
        return rowNo;
    }

    public void setRowNo(int rowNo) {
        this.rowNo = rowNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public String getCheckTimeText() {
        return checkTimeText;
    }

    public void setCheckTimeText(String checkTimeText) {
        this.checkTimeText = checkTimeText;
    }

    public String getHazardCntText() {
        return hazardCntText;
    }

    public void setHazardCntText(String hazardCntText) {
        this.hazardCntText = hazardCntText;
    }

    public String getQtyText() {
        return qtyText;
    }

    public void setQtyText(String qtyText) {
        this.qtyText = qtyText;
    }
}
