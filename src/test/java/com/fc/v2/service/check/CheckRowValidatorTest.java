package com.fc.v2.service.check;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 逐行校验单测：每类毛病各自归码、一行多毛病只取头一个、行号照原表带、好行解析值落地。
 *
 * @author fuce
 * @date 2026-10-03
 */
class CheckRowValidatorTest {

    private final Set<String> known = new HashSet<>(Arrays.asList("EN00", "EN01"));

    /** 接住验过行的解析值。 */
    private static class Holder implements CheckRowValidator.TIsCheckRowLike {
        String siteNo;
        Date checkTime;
        Integer hazardCnt;
        BigDecimal qty;

        @Override
        public void accept(String siteNo, Date checkTime, Integer hazardCnt, BigDecimal qty) {
            this.siteNo = siteNo;
            this.checkTime = checkTime;
            this.hazardCnt = hazardCnt;
            this.qty = qty;
        }
    }

    private CheckError validate(CheckImportRow raw, Holder h) {
        return CheckRowValidator.validate(raw, h, known);
    }

    private CheckImportRow row(int no, String site, String time, String haz, String qty) {
        return new CheckImportRow(no, site, time, haz, qty);
    }

    @Test
    void goodRowPassesAndCarriesParsedValues() {
        Holder h = new Holder();
        CheckError err = validate(row(1, " EN00 ", "2026-09-30 14:30", "3", "88.50"), h);
        assertNull(err);
        assertEquals("EN00", h.siteNo);
        assertEquals(3, h.hazardCnt.intValue());
        assertEquals(0, new BigDecimal("88.50").compareTo(h.qty));
    }

    @Test
    void emptyAllFourCellsIsRowEmpty() {
        CheckError err = validate(row(2, "  ", "", "   ", "  "), new Holder());
        assertNotNull(err);
        assertEquals(2, err.getRowNo(), "行号照原表带，不许抽走");
        assertEquals(CheckConst.ITEM_ROW_EMPTY, err.getItemCode());
    }

    @Test
    void missingSiteIsEntNotFoundAndBlankSiteSameCode() {
        assertEquals(CheckConst.ITEM_ENT_NOT_FOUND,
                validate(row(1, "", "2026-09-30", "1", "80"), new Holder()).getItemCode());
        CheckError e = validate(row(3, "EN99", "2026-09-30", "1", "80"), new Holder());
        assertEquals(CheckConst.ITEM_ENT_NOT_FOUND, e.getItemCode());
        assertTrue(e.getReason().contains("EN99"));
    }

    @Test
    void badTimeShapesAreRejected() {
        for (String bad : new String[]{"", "2026/13/40", "2026-9-30 25:61", "九月三十号", "20260930", "30/09/2026"}) {
            CheckError e = validate(row(1, "EN00", bad, "1", "80"), new Holder());
            assertEquals(CheckConst.ITEM_CHECKTIME_BAD, e.getItemCode(), "时间写法应拒：" + bad);
        }
        // 合法写法全放行
        for (String ok : new String[]{"2026-09-30", "2026/09/30", "2026-09-30 14:30", "2026-09-30 14:30:00"}) {
            Holder h = new Holder();
            assertNull(validate(row(1, "EN00", ok, "1", "80"), h), "时间写法应收：" + ok);
            assertNotNull(h.checkTime);
        }
    }

    @Test
    void hazardOverLimitAndGarbageShareCode() {
        CheckError over = validate(row(1, "EN00", "2026-09-30",
                String.valueOf(CheckConst.MAX_HAZARD_CNT + 1), "80"), new Holder());
        assertEquals(CheckConst.ITEM_HAZARD_OVER_LIMIT, over.getItemCode());
        assertTrue(over.getReason().contains(String.valueOf(CheckConst.MAX_HAZARD_CNT)));

        assertEquals(CheckConst.ITEM_HAZARD_OVER_LIMIT,
                validate(row(1, "EN00", "2026-09-30", "三条", "80"), new Holder()).getItemCode());
        assertEquals(CheckConst.ITEM_HAZARD_OVER_LIMIT,
                validate(row(1, "EN00", "2026-09-30", "-1", "80"), new Holder()).getItemCode());
        assertEquals(CheckConst.ITEM_HAZARD_OVER_LIMIT,
                validate(row(1, "EN00", "2026-09-30", "1.5", "80"), new Holder()).getItemCode());
        // 空着隐患格当 0 条，不因此驳回
        Holder h = new Holder();
        assertNull(validate(row(1, "EN00", "2026-09-30", "", "80"), h));
        assertEquals(0, h.hazardCnt.intValue());
    }

    @Test
    void scoreEmptyNegativeTooLargeBadScaleAndGarbageEachOwnCode() {
        assertEquals(CheckConst.ITEM_QTY_EMPTY,
                validate(row(1, "EN00", "2026-09-30", "0", "  "), new Holder()).getItemCode());
        assertEquals(CheckConst.ITEM_QTY_NEGATIVE,
                validate(row(1, "EN00", "2026-09-30", "0", "-0.01"), new Holder()).getItemCode());
        CheckError big = validate(row(1, "EN00", "2026-09-30", "0", "100.01"), new Holder());
        assertEquals(CheckConst.ITEM_QTY_TOO_LARGE, big.getItemCode());
        // 100 分整是允许的上限
        assertNull(validate(row(1, "EN00", "2026-09-30", "0", "100"), new Holder()));
        // 0 分合法
        assertNull(validate(row(1, "EN00", "2026-09-30", "0", "0"), new Holder()));
        // 小数位敲多（去年就栽在小数位敲错）
        CheckError scale = validate(row(1, "EN00", "2026-09-30", "0", "88.123"), new Holder());
        assertEquals(CheckConst.ITEM_QTY_BAD, scale.getItemCode());
        // 88.50 两位放行；不是数也归 QTY_BAD
        assertNull(validate(row(1, "EN00", "2026-09-30", "0", "88.50"), new Holder()));
        assertEquals(CheckConst.ITEM_QTY_BAD,
                validate(row(1, "EN00", "2026-09-30", "0", "八十八"), new Holder()).getItemCode());
    }

    @Test
    void onlyFirstProblemReportedWhenRowHasMany() {
        // 企业编号和时间都坏：只回企业编号这一个码，不堆叠
        CheckError e = validate(row(7, "EN99", "not-a-date", "99999", "-5"), new Holder());
        assertEquals(CheckConst.ITEM_ENT_NOT_FOUND, e.getItemCode());
        assertEquals(7, e.getRowNo());
    }

    @Test
    void emptyEnterpriseLedgerRejectsEverySite() {
        CheckError e = CheckRowValidator.validate(row(1, "EN00", "2026-09-30", "0", "80"),
                new Holder(), Collections.<String>emptySet());
        assertEquals(CheckConst.ITEM_ENT_NOT_FOUND, e.getItemCode());
    }
}
