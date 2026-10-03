package com.fc.v2.service.check;

import java.util.Collections;
import java.util.List;

/**
 * 整批导入回执。三个数——全批几行、进了几行、退了几行——只由后台逐行处理方法给，
 * 页面既不加行也不动旧批；守恒式 stored + rejected == total 在构造时当场验，不成立直接抛出。
 *
 * outcome 区分四种进门结果：
 * ACCEPTED 正常处理（含空批：0/0/0，但话术绝不写"已核讫"）；
 * DUPLICATE 同批次号二次上传，库里只指头一遍，本次一行未写；
 * OVER_LIMIT 超单批上限，整批退掉、一行不留（头表、行表都不写）。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class CheckReceipt {

    public enum Outcome { ACCEPTED, DUPLICATE, OVER_LIMIT }

    private final Outcome outcome;
    private final String batchNo;
    private final int total;
    private final int stored;
    private final int rejected;
    /** 正式检查记录条数（同企业多行并一条时，小于入库行数；得分只算一次） */
    private final int recordCnt;
    /** 并记行数（被并进前头同企业行的行数） */
    private final int mergedCnt;
    private final List<CheckError> errors;
    private final String note;

    private CheckReceipt(Outcome outcome, String batchNo, int total, int stored, int rejected,
                         int recordCnt, int mergedCnt, List<CheckError> errors, String note) {
        if (stored < 0 || rejected < 0 || total < 0) {
            throw new IllegalArgumentException("回执计数不允许为负");
        }
        if (stored + rejected != total) {
            throw new IllegalStateException(
                    "回执三数对不上：入库" + stored + "+驳回" + rejected + "≠全批" + total
                            + "，逐行点算有误，整批事务应回滚");
        }
        this.outcome = outcome;
        this.batchNo = batchNo;
        this.total = total;
        this.stored = stored;
        this.rejected = rejected;
        this.recordCnt = recordCnt;
        this.mergedCnt = mergedCnt;
        this.errors = errors == null ? Collections.<CheckError>emptyList()
                : Collections.unmodifiableList(errors);
        this.note = note;
    }

    /** 正常处理完（含空批）：三数以库里逐行点算为准。 */
    public static CheckReceipt accepted(String batchNo, int total, int stored, int rejected,
                                        int recordCnt, int mergedCnt,
                                        List<CheckError> errors, String note) {
        return new CheckReceipt(Outcome.ACCEPTED, batchNo, total, stored, rejected,
                recordCnt, mergedCnt, errors, note);
    }

    /** 重复批次：三数从头一遍批次在库里的点算取，本次不写。 */
    public static CheckReceipt duplicate(String batchNo, int total, int stored, int rejected,
                                         int recordCnt, int mergedCnt,
                                         List<CheckError> errors, String note) {
        return new CheckReceipt(Outcome.DUPLICATE, batchNo, total, stored, rejected,
                recordCnt, mergedCnt, errors, note);
    }

    /** 超上限：整批未进门，total 取解析出的行数，入库 0、驳回=全批（库里一行不留）。 */
    public static CheckReceipt overLimit(String batchNo, int parsedTotal, int limit, String note) {
        return new CheckReceipt(Outcome.OVER_LIMIT, batchNo, parsedTotal, 0, parsedTotal,
                0, 0, Collections.<CheckError>emptyList(), note);
    }

    public boolean isDuplicate() {
        return outcome == Outcome.DUPLICATE;
    }

    public boolean isOverLimit() {
        return outcome == Outcome.OVER_LIMIT;
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public int getTotal() {
        return total;
    }

    public int getStored() {
        return stored;
    }

    public int getRejected() {
        return rejected;
    }

    public int getRecordCnt() {
        return recordCnt;
    }

    public int getMergedCnt() {
        return mergedCnt;
    }

    public List<CheckError> getErrors() {
        return errors;
    }

    public String getNote() {
        return note;
    }
}
