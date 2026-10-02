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
 * 整改申请层级超时催办留痕对象 t_is_rect_urge
 *
 * 同一轮同一级只催一次（uk_apply_round_level）；催办只添留痕，不碰申请单各栏。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_is_rect_urge")
@ApiModel(value = "TIsRectUrge", description = "整改申请层级超时催办留痕")
public class TIsRectUrge implements Serializable {
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

    /** 催办时所在轮次 */
    @TableField("round_no")
    @ApiModelProperty(value = "催办时所在轮次")
    private Integer roundNo;

    /** 卡住的层级 */
    @TableField("level_no")
    @ApiModelProperty(value = "卡住的层级")
    private Integer levelNo;

    /** 卡住的层级名 */
    @TableField("level_name")
    @ApiModelProperty(value = "卡住的层级名")
    private String levelName;

    /** 催办时已卡了多少小时 */
    @TableField("overdue_hours")
    @ApiModelProperty(value = "催办时已卡了多少小时")
    private Integer overdueHours;

    /** 催办时刻 */
    @TableField("urged_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "催办时刻")
    private Date urgedAt;

    /** 催办话术 */
    @TableField("detail")
    @ApiModelProperty(value = "催办话术")
    private String detail;

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

    public Integer getOverdueHours() {
        return overdueHours;
    }

    public void setOverdueHours(Integer overdueHours) {
        this.overdueHours = overdueHours;
    }

    public Date getUrgedAt() {
        return urgedAt;
    }

    public void setUrgedAt(Date urgedAt) {
        this.urgedAt = urgedAt;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
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
