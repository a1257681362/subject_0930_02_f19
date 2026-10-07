package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 备件替换记录：每行一次实物换件，只追加不修改。
 * removed_serial_no 强制留存——被换下的旧件序列号必须登记，新件序列号同步装机，
 * 旧件实例置 SCRAPPED 保留备查，不删除。
 */
@TableName("t_maint_part_replacement")
public class PartReplacement extends BaseEntity {

    private Long workOrderId;
    private Long catalogId;
    private String newSerialNo;
    private String removedSerialNo;
    private String replacedBy;
    private LocalDateTime replacedAt;
    private String requestId;
    private String remark;

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public String getNewSerialNo() { return newSerialNo; }
    public void setNewSerialNo(String newSerialNo) { this.newSerialNo = newSerialNo; }
    public String getRemovedSerialNo() { return removedSerialNo; }
    public void setRemovedSerialNo(String removedSerialNo) { this.removedSerialNo = removedSerialNo; }
    public String getReplacedBy() { return replacedBy; }
    public void setReplacedBy(String replacedBy) { this.replacedBy = replacedBy; }
    public LocalDateTime getReplacedAt() { return replacedAt; }
    public void setReplacedAt(LocalDateTime replacedAt) { this.replacedAt = replacedAt; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
