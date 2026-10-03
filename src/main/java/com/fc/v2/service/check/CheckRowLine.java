package com.fc.v2.service.check;

/**
 * 进门时按原文件先后记下的一行原始录文（未校验、未入库）。
 *
 * 行次在进门那一刻定死：表头不算，第一行数据 = 1，之后错误清单照此号定位原表，
 * 谁查退回行都直接拿这个号，不用回头数纸质件。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class CheckRowLine {

    /** 原表数据行次（1 起） */
    private final int rowNo;
    /** 企业编号录文 */
    private final String siteNo;
    /** 检查时间录文（原样，校验时才辨格式） */
    private final String checkDateRaw;
    /** 隐患条目录文 */
    private final String hazardCntRaw;
    /** 本行得分录文（原样，保留小数位敲错的现场） */
    private final String qtyRaw;

    public CheckRowLine(int rowNo, String siteNo, String checkDateRaw, String hazardCntRaw, String qtyRaw) {
        this.rowNo = rowNo;
        this.siteNo = siteNo;
        this.checkDateRaw = checkDateRaw;
        this.hazardCntRaw = hazardCntRaw;
        this.qtyRaw = qtyRaw;
    }

    public int getRowNo() {
        return rowNo;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public String getCheckDateRaw() {
        return checkDateRaw;
    }

    public String getHazardCntRaw() {
        return hazardCntRaw;
    }

    public String getQtyRaw() {
        return qtyRaw;
    }
}
