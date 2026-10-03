package com.fc.v2.service.check;

/**
 * 执法检查记录整批导入领域常量。
 *
 * 行去处只此三档，全由后台逐行处理方法落库，人不手敲；驳回类别码只此一组，
 * 一行同时有多处毛病时按固定顺序只退一个码、附一句原由，已退掉的行不许再补第二句。
 *
 * @author fuce
 * @date 2026-10-03
 */
public final class CheckConst {

    private CheckConst() {
    }

    /** 行去处：待处理（进门先落此档，验完同事务翻成已入库/驳回） */
    public static final int ST_PENDING = 0;
    /** 行去处：已入库（成正式检查记录；同企业被并入行也落此档，但得分不另计） */
    public static final int ST_STORED = 1;
    /** 行去处：驳回（错误清单照原表行号定位） */
    public static final int ST_REJECTED = 2;

    /** 单批行数上限：超了整批退掉、一行不留 */
    public static final int MAX_ROWS_PER_BATCH = 500;
    /** 单条隐患条数上限：超了按本行驳回 */
    public static final int MAX_HAZARD_CNT = 100;
    /** 得分百分制上限：大于此数视为离谱大数 */
    public static final java.math.BigDecimal MAX_SCORE = new java.math.BigDecimal("100");
    /** 得分最多保留两位小数（与库 decimal(12,2) 对齐，多敲小数位按驳回） */
    public static final int MAX_SCORE_SCALE = 2;

    /** 驳回类别码：整行空白（行号照算，不许抽走导致后面错位） */
    public static final String ITEM_ROW_EMPTY = "ROW_EMPTY";
    /** 驳回类别码：企业编号查无此家（含空着没填） */
    public static final String ITEM_ENT_NOT_FOUND = "ENT_NOT_FOUND";
    /** 驳回类别码：检查时间为空或格式不像样 */
    public static final String ITEM_CHECKTIME_BAD = "CHECKTIME_BAD";
    /** 驳回类别码：隐患条数超上限（含非数字、负数） */
    public static final String ITEM_HAZARD_OVER_LIMIT = "HAZARD_OVER_LIMIT";
    /** 驳回类别码：得分空着没填 */
    public static final String ITEM_QTY_EMPTY = "QTY_EMPTY";
    /** 驳回类别码：得分录成负数 */
    public static final String ITEM_QTY_NEGATIVE = "QTY_NEGATIVE";
    /** 驳回类别码：得分是离谱大数（超过百分制上限） */
    public static final String ITEM_QTY_TOO_LARGE = "QTY_TOO_LARGE";
    /** 驳回类别码：得分小数位敲多了 / 根本不是数 */
    public static final String ITEM_QTY_BAD = "QTY_BAD";

    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case ST_PENDING: return "待处理";
            case ST_STORED: return "已入库";
            case ST_REJECTED: return "驳回";
            default: return "非法去处(" + status + ")";
        }
    }

    /** 错误清单上的类别码中文名（"行号+类别+缘由"三段里的中段）。 */
    public static String itemText(String code) {
        if (code == null) {
            return "未知";
        }
        switch (code) {
            case ITEM_ROW_EMPTY: return "整行空白";
            case ITEM_ENT_NOT_FOUND: return "企业编号";
            case ITEM_CHECKTIME_BAD: return "检查时间";
            case ITEM_HAZARD_OVER_LIMIT: return "隐患条数";
            case ITEM_QTY_EMPTY: return "得分空缺";
            case ITEM_QTY_NEGATIVE: return "得分负数";
            case ITEM_QTY_TOO_LARGE: return "得分超上限";
            case ITEM_QTY_BAD: return "得分格式";
            default: return code;
        }
    }
}
