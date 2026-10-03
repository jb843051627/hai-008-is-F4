package com.fc.v2.service.check;

/**
 * 驳回行明细：错误清单一律按"行号 + 类别 + 缘由"三段写，行号照原表逐行对，表头不算。
 *
 * @author fuce
 * @date 2026-10-03
 */
public class CheckError {

    /** 原表行次（第一条数据为 1） */
    private final int rowNo;
    /** 数据项类别码，取 {@link CheckConst} 那一组，不许自造 */
    private final String itemCode;
    /** 一句原由，落库后不许补第二句 */
    private final String reason;

    public CheckError(int rowNo, String itemCode, String reason) {
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

    /** 错误清单一行："第3行｜企业编号｜编号 EN99 底档查无此家"。 */
    public String toLine() {
        return "第" + rowNo + "行｜" + CheckConst.itemText(itemCode) + "｜" + reason;
    }
}
