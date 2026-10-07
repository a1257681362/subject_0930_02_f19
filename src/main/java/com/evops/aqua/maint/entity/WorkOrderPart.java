package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 工单备件需求行：工单计划使用的备件品类与数量。
 * PLANNED 计划 / RESERVED 已锁定在库实例 / REPLACED 已替换装机 / CANCELLED 未使用取消。
 */
@TableName("t_maint_work_order_part")
public class WorkOrderPart extends BaseEntity {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_RESERVED = "RESERVED";
    public static final String STATUS_REPLACED = "REPLACED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private Long workOrderId;
    private Long catalogId;
    private Integer quantity;
    private String status;

    public Long getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(Long workOrderId) { this.workOrderId = workOrderId; }
    public Long getCatalogId() { return catalogId; }
    public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
