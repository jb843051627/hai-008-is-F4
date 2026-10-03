package com.fc.v2.service.check;

import java.util.ArrayList;
import java.util.List;

/**
 * 整批导入回执：三个数以库里逐行点算为准，界面上另摆的不作数。
 *
 * 全批几行 {@link #totalCnt}、进了几行 {@link #storedCnt}、退了几行 {@link #rejectedCnt}，
 * 恒等式 stored + rejected = total；对不上号时 {@link #consistent} 置 false 并把
 * 对不上的行（未落定成入库/退回的）当场挑进 {@link #mismatchRows}。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class BatchReceipt {

    /** 一条错误明细：行号 + 类别码 + 缘由，行号照原表 */
    public static class RowError {
        private final int rowNo;
        private final String itemCode;
        private final String reason;

        public RowError(int rowNo, String itemCode, String reason) {
            this.rowNo = rowNo;
            this.itemCode = itemCode;
            this.reason = reason;
        }

        public int getRowNo() {
            return rowNo;
        }

        public String getItemCode() {
            return itemCode;
        }

        public String getReason() {
            return reason;
        }

        /** 错误清单统一行式：行号 + 类别 + 缘由 */
        public String toLine() {
            return "第" + rowNo + "行 [" + CheckConst.itemText(itemCode) + "] " + reason;
        }
    }

    private String batchNo;
    /** 这批收不收：false=整批没收（超上限/空批/编码不进门），库里一行没有 */
    private boolean accepted = true;
    private boolean duplicate = false;
    private int totalCnt;
    private int storedCnt;
    private int rejectedCnt;
    /** 得分只算一次的口径：同企业并入行不重复计得分（正式检查记录条数） */
    private int formalCnt;
    private boolean consistent = true;
    private String message;
    private List<RowError> errors = new ArrayList<>();
    /** 三数对不上时，当场挑出的行次 */
    private List<Integer> mismatchRows = new ArrayList<>();

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }

    public int getTotalCnt() {
        return totalCnt;
    }

    public void setTotalCnt(int totalCnt) {
        this.totalCnt = totalCnt;
    }

    public int getStoredCnt() {
        return storedCnt;
    }

    public void setStoredCnt(int storedCnt) {
        this.storedCnt = storedCnt;
    }

    public int getRejectedCnt() {
        return rejectedCnt;
    }

    public void setRejectedCnt(int rejectedCnt) {
        this.rejectedCnt = rejectedCnt;
    }

    public int getFormalCnt() {
        return formalCnt;
    }

    public void setFormalCnt(int formalCnt) {
        this.formalCnt = formalCnt;
    }

    public boolean isConsistent() {
        return consistent;
    }

    public void setConsistent(boolean consistent) {
        this.consistent = consistent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<RowError> getErrors() {
        return errors;
    }

    public void setErrors(List<RowError> errors) {
        this.errors = errors;
    }

    public List<Integer> getMismatchRows() {
        return mismatchRows;
    }

    public void setMismatchRows(List<Integer> mismatchRows) {
        this.mismatchRows = mismatchRows;
    }
}
