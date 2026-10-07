package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 备件实例：一物一序列号。
 * IN_STOCK 在库 / RESERVED 工单占用 / INSTALLED 已装机 / SCRAPPED 旧件报废留存。
 * 换件时新件转 INSTALLED、旧件转 SCRAPPED——旧序列号与原装机信息永久保留，不删除不覆盖。
 */
@TableName("t_maint_spare_part")
public class SparePart extends BaseEntity {

    public static final String STATUS_IN_STOCK = "IN_STOCK";
    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_INSTALLED = "INSTALLED";
    public static final String STATUS_SCRAPPED = "SCRAPPED";

    private Long catalogId;
    private String serialNo;
    private String status;
    private Long reservedWorkOrderId;
    private Long installedDeviceId;
    private Long installedWorkOrderId;
    private LocalDateTime installedAt;
    private Long removedWorkOrderId;
    private LocalDateTime removedAt;

    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public String getSerialNo() { return serialNo; }
    public void setSerialNo(String serialNo) { this.serialNo = serialNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getReservedWorkOrderId() { return reservedWorkOrderId; }
    public void setReservedWorkOrderId(Long reservedWorkOrderId) { this.reservedWorkOrderId = reservedWorkOrderId; }
    public Long getInstalledDeviceId() { return installedDeviceId; }
    public void setInstalledDeviceId(Long installedDeviceId) { this.installedDeviceId = installedDeviceId; }
    public Long getInstalledWorkOrderId() { return installedWorkOrderId; }
    public void setInstalledWorkOrderId(Long installedWorkOrderId) { this.installedWorkOrderId = installedWorkOrderId; }
    public LocalDateTime getInstalledAt() { return installedAt; }
    public void setInstalledAt(LocalDateTime installedAt) { this.installedAt = installedAt; }
    public Long getRemovedWorkOrderId() { return removedWorkOrderId; }
    public void setRemovedWorkOrderId(Long removedWorkOrderId) { this.removedWorkOrderId = removedWorkOrderId; }
    public LocalDateTime getRemovedAt() { return removedAt; }
    public void setRemovedAt(LocalDateTime removedAt) { this.removedAt = removedAt; }
}
