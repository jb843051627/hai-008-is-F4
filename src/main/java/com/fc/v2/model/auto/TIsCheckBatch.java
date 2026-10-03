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
 * 执法检查数据上传批次登记对象 t_is_check_batch
 *
 * @author fuce
 * @date 2026-10-03
 */
@TableName("t_is_check_batch")
@ApiModel(value = "TIsCheckBatch", description = "执法检查数据上传批次登记")
public class TIsCheckBatch implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 上传批次号（全库唯一） */
    @TableField("batch_no")
    @ApiModelProperty(value = "上传批次号")
    private String batchNo;

    /** 全批行数（库里逐行点算） */
    @TableField("total_cnt")
    @ApiModelProperty(value = "全批行数")
    private Integer totalCnt;

    /** 已入库行数 */
    @TableField("stored_cnt")
    @ApiModelProperty(value = "已入库行数")
    private Integer storedCnt;

    /** 驳回行数 */
    @TableField("rejected_cnt")
    @ApiModelProperty(value = "驳回行数")
    private Integer rejectedCnt;

    /** 回执话术（空批不写已核讫） */
    @TableField("receipt_note")
    @ApiModelProperty(value = "回执话术")
    private String receiptNote;

    /** 原文件名（备查） */
    @TableField("source_file")
    @ApiModelProperty(value = "原文件名")
    private String sourceFile;

    /** 进门登记时刻 */
    @TableField("received_at")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "进门登记时刻")
    private Date receivedAt;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getBatchNo() {
        return batchNo;
    }

    public void setBatchNo(String batchNo) {
        this.batchNo = batchNo;
    }

    public Integer getTotalCnt() {
        return totalCnt;
    }

    public void setTotalCnt(Integer totalCnt) {
        this.totalCnt = totalCnt;
    }

    public Integer getStoredCnt() {
        return storedCnt;
    }

    public void setStoredCnt(Integer storedCnt) {
        this.storedCnt = storedCnt;
    }

    public Integer getRejectedCnt() {
        return rejectedCnt;
    }

    public void setRejectedCnt(Integer rejectedCnt) {
        this.rejectedCnt = rejectedCnt;
    }

    public String getReceiptNote() {
        return receiptNote;
    }

    public void setReceiptNote(String receiptNote) {
        this.receiptNote = receiptNote;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Date getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Date receivedAt) {
        this.receivedAt = receivedAt;
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
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}
