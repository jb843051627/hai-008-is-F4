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
 * 整改申请签批层级配置对象 t_is_rect_level
 *
 * 层级次序平台写死，不抄进申请单；链条永远现取 status=0 按 level_no 升序。
 *
 * @author fuce
 * @date 2026-10-02
 */
@TableName("t_is_rect_level")
@ApiModel(value = "TIsRectLevel", description = "整改申请签批层级配置")
public class TIsRectLevel implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "主键")
    private Long id;

    /** 层级次序（1起；提交后进最小号，通过进比它大的下一号） */
    @TableField("level_no")
    @ApiModelProperty(value = "层级次序（1起）")
    private Integer levelNo;

    /** 层级名 */
    @TableField("level_name")
    @ApiModelProperty(value = "层级名")
    private String levelName;

    /** 本层签收角色（角色对不上，这层的单连看都看不到） */
    @TableField("signer_role_id")
    @JsonSerialize(using = ToStringSerializer.class)
    @ApiModelProperty(value = "本层签收角色")
    private Long signerRoleId;

    /** 本层签收角色名（抄存） */
    @TableField("signer_role_name")
    @ApiModelProperty(value = "本层签收角色名")
    private String signerRoleName;

    /** 卡在本层的时限（小时）：超时即催办 */
    @TableField("urge_hours")
    @ApiModelProperty(value = "卡在本层的时限（小时）")
    private Integer urgeHours;

    /** 情形 0启用 1停用 */
    @TableField("status")
    @ApiModelProperty(value = "情形 0启用 1停用")
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

    public Long getSignerRoleId() {
        return signerRoleId;
    }

    public void setSignerRoleId(Long signerRoleId) {
        this.signerRoleId = signerRoleId;
    }

    public String getSignerRoleName() {
        return signerRoleName;
    }

    public void setSignerRoleName(String signerRoleName) {
        this.signerRoleName = signerRoleName;
    }

    public Integer getUrgeHours() {
        return urgeHours;
    }

    public void setUrgeHours(Integer urgeHours) {
        this.urgeHours = urgeHours;
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
