package com.fc.v2.service.rect;

/**
 * 整改申请逐级签批领域常量。
 *
 * 状态只此五档，代码里不许出现第六个值；签收只此四动作，作废只此两个入口（撤回/归档）。
 *
 * @author fuce
 * @date 2026-10-02
 */
public final class RectConst {

    private RectConst() {
    }

    /** 申请单状态：未提交 */
    public static final int ST_DRAFT = 0;
    /** 申请单状态：审批中 */
    public static final int ST_RUNNING = 1;
    /** 申请单状态：已通过 */
    public static final int ST_PASSED = 2;
    /** 申请单状态：已驳回 */
    public static final int ST_REJECTED = 3;
    /** 申请单状态：已作废 */
    public static final int ST_VOID = 4;

    /** 结论：通过 */
    public static final int CON_PASS = 1;
    /** 结论：驳回 */
    public static final int CON_REJECT = 2;

    /** 签收动作：通过 */
    public static final String ACT_PASS = "PASS";
    /** 签收动作：驳回 */
    public static final String ACT_REJECT = "REJECT";
    /** 签收动作：补充材料 */
    public static final String ACT_SUPPLEMENT = "SUPPLEMENT";
    /** 签收动作：转交他级 */
    public static final String ACT_TRANSFER = "TRANSFER";

    /** 流程事件：提交 */
    public static final String EVT_SUBMIT = "SUBMIT";
    /** 流程事件：补齐重提 */
    public static final String EVT_RESUBMIT = "RESUBMIT";
    /** 流程事件：申请人撤回 */
    public static final String EVT_WITHDRAW = "WITHDRAW";
    /** 流程事件：管理员归档 */
    public static final String EVT_ARCHIVE = "ARCHIVE";

    /** 默认层级卡办时限（小时），层级未配时兜底 */
    public static final int DEFAULT_URGE_HOURS = 48;

    public static String statusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        switch (status) {
            case ST_DRAFT: return "未提交";
            case ST_RUNNING: return "审批中";
            case ST_PASSED: return "已通过";
            case ST_REJECTED: return "已驳回";
            case ST_VOID: return "已作废";
            default: return "非法状态(" + status + ")";
        }
    }

    public static String actionText(String action) {
        if (action == null) {
            return "未知";
        }
        switch (action) {
            case ACT_PASS: return "通过";
            case ACT_REJECT: return "驳回";
            case ACT_SUPPLEMENT: return "补充材料";
            case ACT_TRANSFER: return "转交他级";
            case EVT_SUBMIT: return "提交";
            case EVT_RESUBMIT: return "补齐重提";
            case EVT_WITHDRAW: return "撤回";
            case EVT_ARCHIVE: return "归档";
            default: return action;
        }
    }
}
