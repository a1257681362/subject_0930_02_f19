package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 履约子单：按「规格 × 收货点」从履约订单拆出；parent_id 非空表示再拆产生的子行。
 * PLANNED → FULFILLING（首次入库）→ DONE（交接单签收）。
 * 金额口径：line_amount = planned_net_kg × unit_price；全部子单行金额合计恒等于订单 total_amount。
 */
@TableName("t_fulfill_delivery")
public class FulfillDelivery extends BaseEntity {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_FULFILLING = "FULFILLING";
    public static final String STATUS_DONE = "DONE";

    private String deliveryNo;
    private Long orderId;
    private Long parentId;
    private Long sourceReservationId;
    private String specCode;
    private Long deliveryPointId;
    private BigDecimal plannedNetKg;
    private BigDecimal unitPrice;
    private BigDecimal lineAmount;
    private BigDecimal intakeNetKg;
    private String status;
    private String requestId;
    private String remark;

    public String getDeliveryNo() { return deliveryNo; }
    public void setDeliveryNo(String deliveryNo) { this.deliveryNo = deliveryNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public Long getSourceReservationId() { return sourceReservationId; }
    public void setSourceReservationId(Long sourceReservationId) { this.sourceReservationId = sourceReservationId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public Long getDeliveryPointId() { return deliveryPointId; }
    public void setDeliveryPointId(Long deliveryPointId) { this.deliveryPointId = deliveryPointId; }
    public BigDecimal getPlannedNetKg() { return plannedNetKg; }
    public void setPlannedNetKg(BigDecimal plannedNetKg) { this.plannedNetKg = plannedNetKg; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getLineAmount() { return lineAmount; }
    public void setLineAmount(BigDecimal lineAmount) { this.lineAmount = lineAmount; }
    public BigDecimal getIntakeNetKg() { return intakeNetKg; }
    public void setIntakeNetKg(BigDecimal intakeNetKg) { this.intakeNetKg = intakeNetKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
