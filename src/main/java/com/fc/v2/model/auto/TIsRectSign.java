package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;
import java.util.Date;

/**
 * 整改申请签收意见留痕对象 t_is_rect_sign
 *
 * 一张单多条，按轮次+层级+时刻排：每一级的意见、签署人、签署时间都在这，一查就有。
 * 代办时实际落笔者 signer、原签署人 intended_signer、代办原因 agent_reason 三栏写全。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_is_rect_sign")
@ApiModel(value = "TIsRectSign", description = "整改申请签收意见留痕")
public class TIsRectSign implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 所属申请单 */
    @TableField("apply_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "所属申请单")
    private Long applyId;

    /** 申请单号（抄存） */
    @TableField("apply_no")
    @ApiModelProperty(value = "申请单号")
    private String applyNo;

    /** 本轮签批轮次（重提后在新轮次里重走第一级） */
    @TableField("round_no")
    @ApiModelProperty(value = "签批轮次")
    private Integer roundNo;

    /** 意见所属层级（流程事件为空） */
    @TableField("level_no")
    @ApiModelProperty(value = "意见所属层级")
    private Integer levelNo;

    /** 层级名（抄存） */
    @TableField("level_name")
    @ApiModelProperty(value = "层级名")
    private String levelName;

    /** 动作 SUBMIT/PASS/REJECT/SUPPLEMENT/TRANSFER/RESUBMIT/WITHDRAW/ARCHIVE */
    @TableField("action")
    @ApiModelProperty(value = "动作")
    private String action;

    /** 实际落笔者（代办时即代办人） */
    @TableField("signer")
    @ApiModelProperty(value = "实际落笔者")
    private String signer;

    /** 原签署人（代办时与落笔者不同；正常签收两者相同） */
    @TableField("intended_signer")
    @ApiModelProperty(value = "原签署人")
    private String intendedSigner;

    /** 是否代办 0否 1是 */
    @TableField("agent_flag")
    @ApiModelProperty(value = "是否代办 0否 1是")
    private Integer agentFlag;

    /** 代办原因（代办必填） */
    @TableField("agent_reason")
    @ApiModelProperty(value = "代办原因")
    private String agentReason;

    /** 转交目标层级号（仅转交） */
    @TableField("target_level_no")
    @ApiModelProperty(value = "转交目标层级号")
    private Integer targetLevelNo;

    /** 转交目标层级名（仅转交） */
    @TableField("target_level_name")
    @ApiModelProperty(value = "转交目标层级名")
    private String targetLevelName;

    /** 本层处理意见（签收/转交必填，转交须把本级意见记全） */
    @TableField("comment")
    @ApiModelProperty(value = "处理意见")
    private String comment;

    /** 签署/事件时刻 */
    @TableField("sign_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "签署时刻")
    private Date signTime;

    /** 创建者 */
    @TableField("create_by")
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField("create_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplyId() {
        return applyId;
    }

    public void setApplyId(Long applyId) {
        this.applyId = applyId;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getLevelNo() {
        return levelNo;
    }

    public void setLevelNo(Integer levelNo) {
        this.levelNo = levelNo;
    }

    public String getLevelName() {
        return levelName;
    }

    public void setLevelName(String levelName) {
        this.levelName = levelName;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getSigner() {
        return signer;
    }

    public void setSigner(String signer) {
        this.signer = signer;
    }

    public String getIntendedSigner() {
        return intendedSigner;
    }

    public void setIntendedSigner(String intendedSigner) {
        this.intendedSigner = intendedSigner;
    }

    public Integer getAgentFlag() {
        return agentFlag;
    }

    public void setAgentFlag(Integer agentFlag) {
        this.agentFlag = agentFlag;
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

    public String getTargetLevelName() {
        return targetLevelName;
    }

    public void setTargetLevelName(String targetLevelName) {
        this.targetLevelName = targetLevelName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Date getSignTime() {
        return signTime;
    }

    public void setSignTime(Date signTime) {
        this.signTime = signTime;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
