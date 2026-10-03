package com.fc.v2.service.check;

/**
 * 执法检查记录整批导入领域常量。
 *
 * 行落地只此三档（0待核/1已入库/2退回），全由系统落库、人不手敲；
 * 退回类别码只此一册，每行最多挂一个码、只附一句原由，码外不许自造。
 *
 * @author fuce
 * @date 2026-10-03
 */
public final class CheckConst {

    private CheckConst() {
    }

    /** 行落地情形：待核（进门登记，处理完必翻成入库或退回，不允许停留） */
    public static final int ST_PENDING = 0;
    /** 行落地情形：已入库（正式检查记录） */
    public static final int ST_OK = 1;
    /** 行落地情形：退回（错误清单照原表行号定位） */
    public static final int ST_FAIL = 2;

    /** 退回类别码：企业编号查无此家（含空着、已退役） */
    public static final String ITEM_SITE_NOT_FOUND = "SITE_NOT_FOUND";
    /** 退回类别码：检查时间格式不像样 */
    public static final String ITEM_CHECK_DATE = "CHECK_DATE_FORMAT";
    /** 退回类别码：隐患条数录得不是整数 */
    public static final String ITEM_HAZARD_CNT_INVALID = "HAZARD_CNT_INVALID";
    /** 退回类别码：隐患条数超上限 */
    public static final String ITEM_HAZARD_CNT_OVER = "HAZARD_CNT_OVER";
    /** 退回类别码：得分空着 */
    public static final String ITEM_QTY_EMPTY = "QTY_EMPTY";
    /** 退回类别码：得分录得不是数字（含小数位敲花） */
    public static final String ITEM_QTY_FORMAT = "QTY_FORMAT";
    /** 退回类别码：得分录成负数（含 0） */
    public static final String ITEM_QTY_NEGATIVE = "QTY_NEGATIVE";
    /** 退回类别码：得分离谱大数（超过满分上限） */
    public static final String ITEM_QTY_TOO_LARGE = "QTY_TOO_LARGE";
    /** 退回类别码：得分小数位超过两位 */
    public static final String ITEM_QTY_SCALE = "QTY_SCALE";

    /** 单批行数上限：超了整批退掉、一行不留 */
    public static final int MAX_ROWS_PER_BATCH = 500;
    /** 单次检查隐患条数上限 */
    public static final int MAX_HAZARD_CNT = 100;
    /** 得分满分（含） */
    public static final java.math.BigDecimal SCORE_MAX = new java.math.BigDecimal("100");
    /** 得分允许的小数位数 */
    public static final int SCORE_SCALE = 2;

    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case ST_PENDING: return "待核";
            case ST_OK: return "已入库";
            case ST_FAIL: return "退回";
            default: return "未知(" + status + ")";
        }
    }

    /** 错误清单统一口径：行号 + 类别 + 缘由，行号与原表逐行对齐（表头不算，第一行数据=1）。 */
    public static String itemText(String code) {
        if (code == null) {
            return "未知";
        }
        switch (code) {
            case ITEM_SITE_NOT_FOUND: return "企业编号查无此家";
            case ITEM_CHECK_DATE: return "检查时间格式不像样";
            case ITEM_HAZARD_CNT_INVALID: return "隐患条数录得不对";
            case ITEM_HAZARD_CNT_OVER: return "隐患条数超上限";
            case ITEM_QTY_EMPTY: return "得分空着";
            case ITEM_QTY_FORMAT: return "得分录得不是数字";
            case ITEM_QTY_NEGATIVE: return "得分录成负数";
            case ITEM_QTY_TOO_LARGE: return "得分离谱大数";
            case ITEM_QTY_SCALE: return "得分小数位超限";
            default: return code;
        }
    }
}
