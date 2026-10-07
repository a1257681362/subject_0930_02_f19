package com.evops.aqua.fulfill.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 履约拆单行：关联一条来源捕捞预约，指定规格、收货点、计划净重与协议单价。
 */
public class FulfillOrderLineRequest {

    @NotNull(message = "来源捕捞预约id不能为空")
    private Long reservationId;

    @NotBlank(message = "规格编码不能为空")
    private String specCode;

    @NotNull(message = "收货点不能为空")
    private Long deliveryPointId;

    @NotNull(message = "计划净重不能为空")
    private BigDecimal plannedNetKg;

    @NotNull(message = "协议单价不能为空")
    private BigDecimal unitPrice;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public Long getDeliveryPointId() { return deliveryPointId; }
    public void setDeliveryPointId(Long deliveryPointId) { this.deliveryPointId = deliveryPointId; }
    public BigDecimal getPlannedNetKg() { return plannedNetKg; }
    public void setPlannedNetKg(BigDecimal plannedNetKg) { this.plannedNetKg = plannedNetKg; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
}
