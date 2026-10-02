package com.fc.v2.service.rect;

/**
 * 一次签收动作的入参。
 *
 * operator：当前登录人，永远是实际落笔者；
 * intendedSigner：原签署人——留空或与 operator 相同即本人签收，填了别人且给了代办原因才算代办。
 * 代办不等于换人：单子仍挂在原层级，留痕上原签署人该是谁还是谁。
 *
 * @author fuce
 * @date 2026-10-02
 */
public class SignCmd {

    /** 申请单 id */
    private Long id;
    /** 动作：PASS/REJECT/SUPPLEMENT/TRANSFER */
    private String action;
    /** 实际落笔者（当前登录人） */
    private String operator;
    /** 本人意见 */
    private String comment;
    /** 原签署人（代办时填，可空） */
    private String intendedSigner;
    /** 代办原因（代办必填） */
    private String agentReason;
    /** 转交目标层级号（仅转交必填） */
    private Integer targetLevelNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getIntendedSigner() {
        return intendedSigner;
    }

    public void setIntendedSigner(String intendedSigner) {
        this.intendedSigner = intendedSigner;
    }

    public String getAgentReason() {
        return agentReason;
    }

    public void setAgentReason(String agentReason) {
        this.agentReason = agentReason;
    }

    public Integer getTargetLevelNo() {
        return targetLevelNo;
    }

    public void setTargetLevelNo(Integer targetLevelNo) {
        this.targetLevelNo = targetLevelNo;
    }
}
