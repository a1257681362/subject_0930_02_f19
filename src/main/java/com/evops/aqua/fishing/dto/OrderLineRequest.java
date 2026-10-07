package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 客户订单明细行：规格 + 重量区间 + 交付时间 + 计划量，一行拆分为一条捕捞预约。
 */
public class OrderLineRequest {

    @NotBlank(message = "规格不能为空")
    private String specCode;

    @NotNull(message = "重量区间下限不能为空")
    private BigDecimal weightMinG;

    @NotNull(message = "重量区间上限不能为空")
    private BigDecimal weightMaxG;

    @NotNull(message = "交付时间不能为空")
    private LocalDateTime deliveryTime;

    @NotNull(message = "计划量不能为空")
    private BigDecimal plannedQtyKg;

    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getWeightMinG() { return weightMinG; }
    public void setWeightMinG(BigDecimal weightMinG) { this.weightMinG = weightMinG; }
    public BigDecimal getWeightMaxG() { return weightMaxG; }
    public void setWeightMaxG(BigDecimal weightMaxG) { this.weightMaxG = weightMaxG; }
    public LocalDateTime getDeliveryTime() { return deliveryTime; }
    public void setDeliveryTime(LocalDateTime deliveryTime) { this.deliveryTime = deliveryTime; }
    public BigDecimal getPlannedQtyKg() { return plannedQtyKg; }
    public void setPlannedQtyKg(BigDecimal plannedQtyKg) { this.plannedQtyKg = plannedQtyKg; }
}
