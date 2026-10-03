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
import java.math.BigDecimal;
import java.util.Date;

/**
 * 执法检查记录导入核对行对象 t_is_check_row
 *
 * @author fuce
 * @date 2026-09-12
 */
@TableName("t_is_check_row")
@ApiModel(value = "TIsCheckRow", description = "执法检查记录导入核对行")
public class TIsCheckRow implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 执法检查数据上传批次号 */
    @TableField("batch_no")
    @ApiModelProperty(value = "执法检查数据上传批次号")
    private String batchNo;

    /** 原表行次 */
    @TableField("row_no")
    @ApiModelProperty(value = "原表行次")
    private Integer rowNo;

    /** 被校验出的数据项类别码（驳回行必填，已入库行为空） */
    @TableField("item_code")
    @ApiModelProperty(value = "被校验出的数据项类别码")
    private String itemCode;

    /** 被查企业编号（照查企业底档） */
    @TableField("site_no")
    @ApiModelProperty(value = "被查企业编号")
    private String siteNo;

    /** 检查时间 */
    @TableField("check_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @ApiModelProperty(value = "检查时间")
    private Date checkTime;

    /** 隐患条数 */
    @TableField("hazard_cnt")
    @ApiModelProperty(value = "隐患条数")
    private Integer hazardCnt;

    /** 并入本行的后续同企业行次清单（逗号分隔，仅合并头行有值） */
    @TableField("merged_row_no")
    @ApiModelProperty(value = "并入本行的后续同企业行次清单")
    private String mergedRowNo;

    /** 本行被并入的前头同企业行次（非空=被并行，得分不另计、不单列正式记录） */
    @TableField("merge_head_row_no")
    @ApiModelProperty(value = "本行被并入的前头同企业行次")
    private Integer mergeHeadRowNo;

    /** 本行检查得分 */
    @TableField("qty")
    @ApiModelProperty(value = "本行检查得分")
    private BigDecimal qty;

    /** 行落地情形 0待处理 1已入库 2驳回 */
    @TableField("status")
    @ApiModelProperty(value = "行落地情形 0待处理 1已入库 2驳回")
    private Integer status;

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

    /** 备注 */
    @TableField("remark")
    @ApiModelProperty(value = "备注")
    private String remark;

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

    public Integer getRowNo() {
        return rowNo;
    }

    public void setRowNo(Integer rowNo) {
        this.rowNo = rowNo;
    }

    public String getItemCode() {
        return itemCode;
    }

    public void setItemCode(String itemCode) {
        this.itemCode = itemCode;
    }

    public String getSiteNo() {
        return siteNo;
    }

    public void setSiteNo(String siteNo) {
        this.siteNo = siteNo;
    }

    public Date getCheckTime() {
        return checkTime;
    }

    public void setCheckTime(Date checkTime) {
        this.checkTime = checkTime;
    }

    public Integer getHazardCnt() {
        return hazardCnt;
    }

    public void setHazardCnt(Integer hazardCnt) {
        this.hazardCnt = hazardCnt;
    }

    public String getMergedRowNo() {
        return mergedRowNo;
    }

    public void setMergedRowNo(String mergedRowNo) {
        this.mergedRowNo = mergedRowNo;
    }

    public Integer getMergeHeadRowNo() {
        return mergeHeadRowNo;
    }

    public void setMergeHeadRowNo(Integer mergeHeadRowNo) {
        this.mergeHeadRowNo = mergeHeadRowNo;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
