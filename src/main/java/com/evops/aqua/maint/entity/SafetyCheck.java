package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 安全确认项：设备恢复运行（工单关闭）前逐项确认。
 * 任一项停留 PENDING 都不允许关闭工单、不允许设备恢复运行。
 */
@TableName("t_maint_safety_check")
public class SafetyCheck extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    private Long workOrderId;
    private String itemCode;
    private String itemName;
    private String status;
    private String confirmedBy;
    private LocalDateTime confirmedAt;
    private String remark;

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
