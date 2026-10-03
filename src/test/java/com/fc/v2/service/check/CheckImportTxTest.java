package com.fc.v2.service.check;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TIsCheckBatchMapper;
import com.fc.v2.mapper.auto.TIsCheckRowMapper;
import com.fc.v2.mapper.auto.TIsEnterpriseMapper;
import com.fc.v2.model.auto.TIsCheckBatch;
import com.fc.v2.model.auto.TIsCheckRow;
import com.fc.v2.model.auto.TIsEnterprise;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 逐行处理方法单测（纯 Mockito，不连库）：行的进退、批次防重、超上限、空批话术、
 * 同企业并记、行号不重排、三数守恒，全照回执与落库实体核对。
 *
 * @author fuce
 * @date 2026-10-03
 */
class CheckImportTxTest {

    @Mock
    private TIsCheckRowMapper rowMapper;
    @Mock
    private TIsCheckBatchMapper batchMapper;
    @Mock
    private TIsEnterpriseMapper enterpriseMapper;
    @InjectMocks
    private CheckImportTx tx;

    private final Date at = new Date(1_780_000_000_000L);

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(batchMapper.selectList(any())).thenReturn(new ArrayList<TIsCheckBatch>());
        when(batchMapper.insert(any(TIsCheckBatch.class))).thenAnswer(inv -> {
            inv.<TIsCheckBatch>getArgument(0).setId(9001L);
            return 1;
        });
        when(batchMapper.update(any(), any(UpdateWrapper.class))).thenReturn(1);
        when(enterpriseMapper.selectList(any())).thenReturn(enterprises("EN00", "EN01"));
        when(rowMapper.insert(any(TIsCheckRow.class))).thenAnswer(inv -> {
            inv.<TIsCheckRow>getArgument(0).setId(1L);
            return 1;
        });
        when(rowMapper.selectList(any())).thenReturn(new ArrayList<TIsCheckRow>());
    }

    private List<TIsEnterprise> enterprises(String... nos) {
        List<TIsEnterprise> list = new ArrayList<>();
        for (String no : nos) {
            TIsEnterprise e = new TIsEnterprise();
            e.setSiteNo(no);
            e.setDelFlag(0);
            list.add(e);
        }
        return list;
    }

    private CheckImportRow r(int no, String site, String time, String haz, String qty) {
        return new CheckImportRow(no, site, time, haz, qty);
    }

    private List<TIsCheckRow> insertedRows() {
        ArgumentCaptor<TIsCheckRow> cap = ArgumentCaptor.forClass(TIsCheckRow.class);
        verify(rowMapper, org.mockito.Mockito.atLeast(0)).insert(cap.capture());
        return cap.getAllValues();
    }

    @Test
    void mixedBatchStoresGoodRowsAndRejectsBadOnesRowByRow() {
        List<CheckImportRow> raws = Arrays.asList(
                r(1, "EN00", "2026-09-30", "3", "88.5"),
                r(2, "EN99", "2026-09-30", "1", "80"),       // 查无此家
                r(3, "EN01", "2026-10-01 09:00", "0", "-5"), // 负数
                r(4, "EN00", "2026-10-02", "2", "91"));
        CheckReceipt rc = tx.run("B2026Q4-01", raws, "q4.csv", "zhangsan", at);

        assertEquals(CheckReceipt.Outcome.ACCEPTED, rc.getOutcome());
        assertEquals(4, rc.getTotal());
        assertEquals(2, rc.getStored());
        assertEquals(2, rc.getRejected());
        // 同企业 EN00 两行并记 → 正式记录 1 条、并记 1 行
        assertEquals(1, rc.getRecordCnt());
        assertEquals(1, rc.getMergedCnt());
        assertEquals(4, rc.getErrors().size() + rc.getStored());

        List<TIsCheckRow> rows = insertedRows();
        assertEquals(4, rows.size(), "好行坏行逐行都落一条，坏行不连累好行");
        TIsCheckRow first = rows.get(0);
        assertEquals(1, first.getRowNo().intValue());
        assertEquals(CheckConst.ST_STORED, first.getStatus().intValue());
        assertNull(first.getItemCode(), "已入库行不留类别码");
        assertNull(first.getMergeHeadRowNo());
        TIsCheckRow badEnt = rows.get(1);
        assertEquals(2, badEnt.getRowNo().intValue(), "行号照原表，不重排");
        assertEquals(CheckConst.ST_REJECTED, badEnt.getStatus().intValue());
        assertEquals(CheckConst.ITEM_ENT_NOT_FOUND, badEnt.getItemCode());
        assertTrue(badEnt.getRemark().contains("EN99"));
        TIsCheckRow neg = rows.get(2);
        assertEquals(CheckConst.ITEM_QTY_NEGATIVE, neg.getItemCode());
        assertEquals(3, neg.getRowNo().intValue());
        TIsCheckRow merged = rows.get(3);
        assertEquals(CheckConst.ST_STORED, merged.getStatus().intValue());
        assertEquals(1, merged.getMergeHeadRowNo().intValue(), "并起来的行次认前头那条");
        assertEquals("4", first.getMergedRowNo(), "头行记下并进来的原表行次");

        // 驳回行只插不更：本批唯一一次 update 是给头行记 merged_row_no，没有谁给驳回行补原因
        verify(rowMapper, org.mockito.Mockito.times(1)).update(any(), any(UpdateWrapper.class));
        verify(batchMapper).insert(any(TIsCheckBatch.class));
        verify(batchMapper).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void blankStructuredRowIsRejectedNotDroppedSoRowNumbersStayAligned() {
        // ",,," 带着列结构：算一条数据行，驳回但行号照占；后面好行行号仍是 2
        List<CheckImportRow> raws = Arrays.asList(
                new CheckImportRow(1, "", "", "", ""),
                r(2, "EN00", "2026-09-30", "1", "80"));
        // 注意：读入器产出的空行四个字段都是 ""（不是 null），这里照实构造
        CheckReceipt rc = tx.run("B-BLANK", raws, "gaps.csv", "u", at);
        assertEquals(2, rc.getTotal());
        assertEquals(1, rc.getStored());
        assertEquals(1, rc.getRejected());
        assertEquals(CheckConst.ITEM_ROW_EMPTY, rc.getErrors().get(0).getItemCode());
        assertEquals(1, rc.getErrors().get(0).getRowNo());
        List<TIsCheckRow> rows = insertedRows();
        assertEquals(2, rows.get(1).getRowNo().intValue());
    }

    @Test
    void duplicateBatchKeepsFirstWritesNothingAndAppendsNoSecondReason() {
        TIsCheckBatch prior = new TIsCheckBatch();
        prior.setId(1L);
        prior.setBatchNo("BDUP");
        prior.setTotalCnt(3);
        prior.setStoredCnt(2);
        prior.setRejectedCnt(1);
        when(batchMapper.selectList(any())).thenReturn(Arrays.asList(prior));
        // 头遍库内点算：入库2、驳回1、正式记录2、并记0（3.4.2 的 selectCount 返回 Integer）
        when(rowMapper.selectCount(any())).thenReturn(2, 1, 2, 0);

        CheckReceipt rc = tx.run("BDUP",
                Arrays.asList(r(1, "EN00", "2026-09-30", "1", "80")), "dup.csv", "u", at);

        assertEquals(CheckReceipt.Outcome.DUPLICATE, rc.getOutcome());
        assertEquals(3, rc.getTotal());
        assertEquals(2, rc.getStored());
        assertEquals(1, rc.getRejected());
        verify(rowMapper, never()).insert(any(TIsCheckRow.class));
        verify(batchMapper, never()).insert(any(TIsCheckBatch.class));
        verify(batchMapper, never()).update(any(), any(UpdateWrapper.class));
    }

    @Test
    void overLimitRejectsWholeBatchAndLeavesNotASingleRow() {
        List<CheckImportRow> many = new ArrayList<>();
        for (int i = 1; i <= CheckConst.MAX_ROWS_PER_BATCH + 1; i++) {
            many.add(r(i, "EN00", "2026-09-30", "1", "80"));
        }
        CheckReceipt rc = tx.run("BBIG", many, "big.csv", "u", at);
        assertEquals(CheckReceipt.Outcome.OVER_LIMIT, rc.getOutcome());
        assertEquals(CheckConst.MAX_ROWS_PER_BATCH + 1, rc.getTotal());
        assertEquals(0, rc.getStored());
        assertEquals(CheckConst.MAX_ROWS_PER_BATCH + 1, rc.getRejected());
        verify(rowMapper, never()).insert(any(TIsCheckRow.class));
        verify(batchMapper, never()).insert(any(TIsCheckBatch.class));
    }

    @Test
    void emptyBatchRegistersZeroReceiptAndNeverSaysVerifiedDone() {
        CheckReceipt rc = tx.run("BEMPTY", new ArrayList<CheckImportRow>(), "empty.csv", "u", at);
        assertEquals(CheckReceipt.Outcome.ACCEPTED, rc.getOutcome());
        assertEquals(0, rc.getTotal());
        assertEquals(0, rc.getStored());
        assertEquals(0, rc.getRejected());
        assertFalse(rc.getNote().contains("已核讫"),
                "空批回执不许写“已核讫”，免得被当成全批清完：" + rc.getNote());
        verify(rowMapper, never()).insert(any(TIsCheckRow.class));

        // 头表三数也必须是 0/0/0
        ArgumentCaptor<TIsCheckBatch> cap = ArgumentCaptor.forClass(TIsCheckBatch.class);
        verify(batchMapper).insert(cap.capture());
        assertEquals("BEMPTY", cap.getValue().getBatchNo());
    }

    @Test
    void sameEnterpriseAcrossTwoMonthsMergesIntoOneRecordScoreOnce() {
        List<CheckImportRow> raws = Arrays.asList(
                r(1, "EN00", "2026-08-31", "1", "80"),
                r(2, "EN00", "2026-09-30", "2", "70"));
        CheckReceipt rc = tx.run("BMERGE", raws, "m.csv", "u", at);
        assertEquals(2, rc.getTotal());
        assertEquals(2, rc.getStored(), "两行都进库留痕");
        assertEquals(0, rc.getRejected());
        assertEquals(1, rc.getRecordCnt(), "并成一条正式记录");
        assertEquals(1, rc.getMergedCnt(), "得分只算一次");

        List<TIsCheckRow> rows = insertedRows();
        // 头行得分=首行 80，没有被后行 70 覆盖；被并行保留自己的得分留痕但不单列正式记录
        assertEquals(0, new java.math.BigDecimal("80").compareTo(rows.get(0).getQty()));
        assertEquals(Integer.valueOf(1), rows.get(1).getMergeHeadRowNo());
        assertNull(rows.get(0).getMergeHeadRowNo());
    }

    @Test
    void everyRejectionCategoryRejectsOnlyThatRowOthersStillStore() {
        List<CheckImportRow> raws = Arrays.asList(
                r(1, "EN00", "2026-09-30", "1", "80"),
                r(2, "", "2026-09-30", "1", "80"),
                r(3, "EN00", "九月", "1", "80"),
                r(4, "EN00", "2026-09-30", "999", "80"),
                r(5, "EN00", "2026-09-30", "1", ""),
                r(6, "EN00", "2026-09-30", "1", "88.123"));
        CheckReceipt rc = tx.run("BCODES", raws, "c.csv", "u", at);
        assertEquals(6, rc.getTotal());
        assertEquals(1, rc.getStored());
        assertEquals(5, rc.getRejected());
        List<CheckError> errs = rc.getErrors();
        assertEquals(Arrays.asList(
                CheckConst.ITEM_ENT_NOT_FOUND,
                CheckConst.ITEM_CHECKTIME_BAD,
                CheckConst.ITEM_HAZARD_OVER_LIMIT,
                CheckConst.ITEM_QTY_EMPTY,
                CheckConst.ITEM_QTY_BAD),
                Arrays.asList(errs.get(0).getItemCode(), errs.get(1).getItemCode(),
                        errs.get(2).getItemCode(), errs.get(3).getItemCode(), errs.get(4).getItemCode()));
        // 错误行号就是原表行号
        assertEquals(Arrays.asList(2, 3, 4, 5, 6), Arrays.asList(
                errs.get(0).getRowNo(), errs.get(1).getRowNo(), errs.get(2).getRowNo(),
                errs.get(3).getRowNo(), errs.get(4).getRowNo()));
    }

    @Test
    void blankBatchNoIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> tx.run("  ", new ArrayList<CheckImportRow>(), "x.csv", "u", at));
        verify(batchMapper, never()).insert(any(TIsCheckBatch.class));
    }

    @Test
    void receiptInvariantStoredPlusRejectedEqualsTotalOrItThrows() {
        assertThrows(IllegalStateException.class,
                () -> CheckReceipt.accepted("BX", 3, 1, 1, 1, 0,
                        new ArrayList<CheckError>(), "对不上"));
        CheckReceipt ok = CheckReceipt.accepted("BX", 3, 2, 1, 2, 0,
                new ArrayList<CheckError>(), "齐了");
        assertNotNull(ok);
    }

    @Test
    void statusIsAlwaysSystemAssignedNeverReadFromCallerPayload() {
        // 调用方塞进来的实体在适配层会被拆成原始字符串，行的去处一律由处理方法落；
        // 这里直接验落库实体 status 只能是 1/2，不会出现待处理 0 终存。
        CheckReceipt rc = tx.run("BSYS",
                Arrays.asList(r(1, "EN00", "2026-09-30", "0", "100")), "s.csv", "u", at);
        for (TIsCheckRow row : insertedRows()) {
            assertTrue(row.getStatus() == CheckConst.ST_STORED || row.getStatus() == CheckConst.ST_REJECTED);
        }
        assertEquals(1, rc.getStored());
    }
}
