package com.fc.v2.service.check;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * 一行归一行的校验：按格顺序挑毛病，一行多处毛病只取头一个类别码、附一句原由，
 * 同批别的行验它们的、互不连累。企业名册由调用方一次查库后传入（同企业跨行合并、批量验都复用这一份）。
 *
 * 验过没毛病的行，解析出的检查时间/隐患条数/得分当场写进目标实体，入库前不再二次敲。
 *
 * @author fuce
 * @date 2026-10-03
 */
public final class CheckRowValidator {

    /** 检查时间认这几种写法（分隔符 - 或 /，时分秒可只到分或全省）；其余一律"看着不像样"。 */
    private static final List<String> DATE_PATTERNS = Arrays.asList(
            "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm:ss", "yyyy/MM/dd HH:mm", "yyyy/MM/dd");

    private CheckRowValidator() {
    }

    /**
     * 校验文件读入的一行原始字符串，并把解析值落到 target。
     *
     * @return null=没毛病；否则为"行号+类别+缘由"的驳回明细（一行只回一个码）
     */
    public static CheckError validate(CheckImportRow raw, TIsCheckRowLike target, Set<String> knownSiteNos) {
        int rowNo = raw.getRowNo();

        if (raw.isBlank()) {
            return new CheckError(rowNo, CheckConst.ITEM_ROW_EMPTY, "整行四格全空，没有可入库的内容");
        }

        String siteNo = trim(raw.getSiteNo());
        if (siteNo.isEmpty()) {
            return new CheckError(rowNo, CheckConst.ITEM_ENT_NOT_FOUND, "企业编号空着没填，底档无从查对");
        }
        if (knownSiteNos == null || !knownSiteNos.contains(siteNo)) {
            return new CheckError(rowNo, CheckConst.ITEM_ENT_NOT_FOUND,
                    "企业编号 " + siteNo + " 在底档里查无此家（注意编号大小写、前后空格）");
        }

        String timeText = trim(raw.getCheckTimeText());
        Date checkTime = parseTime(timeText);
        if (checkTime == null) {
            return new CheckError(rowNo, CheckConst.ITEM_CHECKTIME_BAD,
                    "检查时间“" + timeText + "”格式不像样，应如 2026-09-30 或 2026-09-30 14:30");
        }

        String hazardText = trim(raw.getHazardCntText());
        Integer hazardCnt = parseHazardCnt(hazardText);
        if (hazardCnt == null) {
            return new CheckError(rowNo, CheckConst.ITEM_HAZARD_OVER_LIMIT,
                    "隐患条数“" + hazardText + "”不是整数条，应填 0 及以上的整数");
        }
        if (hazardCnt > CheckConst.MAX_HAZARD_CNT) {
            return new CheckError(rowNo, CheckConst.ITEM_HAZARD_OVER_LIMIT,
                    "隐患条数 " + hazardCnt + " 超过单条上限 " + CheckConst.MAX_HAZARD_CNT + " 条");
        }

        String qtyText = trim(raw.getQtyText());
        if (qtyText.isEmpty()) {
            return new CheckError(rowNo, CheckConst.ITEM_QTY_EMPTY, "本行得分空着没填");
        }
        BigDecimal qty = parseScore(qtyText);
        if (qty == null) {
            return new CheckError(rowNo, CheckConst.ITEM_QTY_BAD,
                    "得分“" + qtyText + "”不是合法数字，小数位最多 " + CheckConst.MAX_SCORE_SCALE + " 位");
        }
        if (qty.signum() < 0) {
            return new CheckError(rowNo, CheckConst.ITEM_QTY_NEGATIVE,
                    "得分 " + qty + " 录成了负数，得分只许填 0 及以上");
        }
        if (qty.compareTo(CheckConst.MAX_SCORE) > 0) {
            return new CheckError(rowNo, CheckConst.ITEM_QTY_TOO_LARGE,
                    "得分 " + qty + " 是离谱大数，百分制最高 " + CheckConst.MAX_SCORE + " 分");
        }

        target.accept(siteNo, checkTime, hazardCnt, qty);
        return null;
    }

    /** 解析检查时间；严格不进位（13 月、32 日都拒）。 */
    static Date parseTime(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        String t = text.trim();
        for (String p : DATE_PATTERNS) {
            SimpleDateFormat sdf = new SimpleDateFormat(p);
            sdf.setLenient(false);
            try {
                Date d = sdf.parse(t);
                // parse 对 "2026-09-30 14" 这类前缀匹配可能放行，回头格式化成同一 pattern 必须严丝合缝。
                if (sdf.format(d).equals(t)) {
                    return d;
                }
            } catch (ParseException ignore) {
                // 试下一种写法
            }
        }
        return null;
    }

    /** 隐患条数：空当 0 条；非整数/负数/带小数一律拒。 */
    static Integer parseHazardCnt(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        String t = text.trim();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < '0' || c > '9') {
                return null;
            }
        }
        try {
            return Integer.valueOf(t);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 得分：数字、小数位不超过两位；负数与大数留给各自的类别码，这里先放行。 */
    static BigDecimal parseScore(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        BigDecimal qty;
        try {
            qty = new BigDecimal(t);
        } catch (NumberFormatException e) {
            return null;
        }
        // new BigDecimal("1.20") scale=2 放行；stripTrailingZeros 后再看多余的敲位。
        try {
            BigDecimal stripped = qty.stripTrailingZeros();
            int scale = stripped.scale() < 0 ? 0 : stripped.scale();
            if (scale > CheckConst.MAX_SCORE_SCALE) {
                return null;
            }
        } catch (ArithmeticException e) {
            return null;
        }
        return qty;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    /** 校验通过时把解析值交给调用方落库，避免校验器直接绑死实体类型。 */
    public interface TIsCheckRowLike {
        void accept(String siteNo, Date checkTime, Integer hazardCnt, BigDecimal qty);
    }
}
