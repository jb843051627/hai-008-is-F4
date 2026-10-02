package com.fc.v2.model.auto;

import com.baomidou.mybatisplus.annotation.FieldFill;
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
 * 隐患整改申请单对象 t_is_rect_apply
 *
 * 一行一张，字段固定：申请单号、关联隐患（哪条、什么级别）、申请人、提交时间、
 * 当前待签层级、状态、结论、结案时间。状态只在五档内取值，不许自造。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_is_rect_apply")
@ApiModel(value = "TIsRectApply", description = "隐患整改申请单")
public class TIsRectApply implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 申请单号 */
    @TableField("apply_no")
    @ApiModelProperty(value = "申请单号")
    private String applyNo;

    /** 关联隐患（哪条） */
    @TableField("hazard_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "关联隐患")
    private Long hazardId;

    /** 关联隐患号（抄存） */
    @TableField("hazard_no")
    @ApiModelProperty(value = "关联隐患号")
    private String hazardNo;

    /** 关联隐患级别 1一级 2二级 3三级（落单后不许改） */
    @TableField("hazard_level")
    @ApiModelProperty(value = "关联隐患级别 1一级 2二级 3三级")
    private Integer hazardLevel;

    /** 申请人 */
    @TableField("applicant")
    @ApiModelProperty(value = "申请人")
    private String applicant;

    /** 最近一次提交时间（补材料重提刷新为重提时刻） */
    @TableField("submit_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "提交时间")
    private Date submitTime;

    /** 当前待签层级（只此一个；未提交/结案后为空） */
    @TableField("cur_level_no")
    @ApiModelProperty(value = "当前待签层级")
    private Integer curLevelNo;

    /** 状态 0未提交 1审批中 2已通过 3已驳回 4已作废 */
    @TableField("status")
    @ApiModelProperty(value = "状态 0未提交 1审批中 2已通过 3已驳回 4已作废")
    private Integer status;

    /** 结论 1通过 2驳回（结案写死，之前为空） */
    @TableField("conclusion")
    @ApiModelProperty(value = "结论 1通过 2驳回")
    private Integer conclusion;

    /** 结案时间（结案写死，之前为空） */
    @TableField("closed_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "结案时间")
    private Date closedTime;

    /** 当前签批轮次（首轮1；每补材料重提一轮加一） */
    @TableField("round_no")
    @ApiModelProperty(value = "当前签批轮次")
    private Integer roundNo;

    /** 进入当前待签层级的时刻（催办计时起点） */
    @TableField("level_entered_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "进入当前待签层级的时刻")
    private Date levelEnteredAt;

    /** 删除标记 0正常 1删除 */
    @TableField("del_flag")
    @ApiModelProperty(value = "删除标记 0正常 1删除")
    private Integer delFlag;

    /** 创建者 */
    @TableField(value = "create_by", fill = FieldFill.INSERT)
    @ApiModelProperty(value = "创建者")
    private String createBy;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "创建时间")
    private Date createTime;

    /** 更新者 */
    @TableField(value = "update_by", fill = FieldFill.UPDATE)
    @ApiModelProperty(value = "更新者")
    private String updateBy;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "更新时间")
    private Date updateTime;

    /** 备注（申请事由/补齐说明） */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getApplyNo() {
        return applyNo;
    }

    public void setApplyNo(String applyNo) {
        this.applyNo = applyNo;
    }

    public Long getHazardId() {
        return hazardId;
    }

    public void setHazardId(Long hazardId) {
        this.hazardId = hazardId;
    }

    public String getHazardNo() {
        return hazardNo;
    }

    public void setHazardNo(String hazardNo) {
        this.hazardNo = hazardNo;
    }

    public Integer getHazardLevel() {
        return hazardLevel;
    }

    public void setHazardLevel(Integer hazardLevel) {
        this.hazardLevel = hazardLevel;
    }

    public String getApplicant() {
        return applicant;
    }

    public void setApplicant(String applicant) {
        this.applicant = applicant;
    }

    public Date getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(Date submitTime) {
        this.submitTime = submitTime;
    }

    public Integer getCurLevelNo() {
        return curLevelNo;
    }

    public void setCurLevelNo(Integer curLevelNo) {
        this.curLevelNo = curLevelNo;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getConclusion() {
        return conclusion;
    }

    public void setConclusion(Integer conclusion) {
        this.conclusion = conclusion;
    }

    public Date getClosedTime() {
        return closedTime;
    }

    public void setClosedTime(Date closedTime) {
        this.closedTime = closedTime;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Date getLevelEnteredAt() {
        return levelEnteredAt;
    }

    public void setLevelEnteredAt(Date levelEnteredAt) {
        this.levelEnteredAt = levelEnteredAt;
    }

    public Integer getDelFlag() {
        return delFlag;
    }

    public void setDelFlag(Integer delFlag) {
        this.delFlag = delFlag;
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

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return this.updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
