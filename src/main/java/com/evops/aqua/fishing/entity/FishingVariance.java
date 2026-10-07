package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 差异单：预约完成时实际量与计划量不一致即生成（SHORT 短装 / OVER 超装），
 * 每预约至多一条；数量一致不生成。
 */
@TableName("t_fishing_variance")
public class FishingVariance extends BaseEntity {

    public static final String TYPE_SHORT = "SHORT";
    public static final String TYPE_OVER = "OVER";

    public static final String STATUS_OPEN = "OPEN";

    private String varianceNo;
    private Long reservationId;
    private Long orderId;
    private String varianceType;
    private BigDecimal plannedQtyKg;
    private BigDecimal actualQtyKg;
    private BigDecimal diffQtyKg;
    private String status;
    private String createdBy;
    private String requestId;
    private String remark;

    public String getVarianceNo() { return varianceNo; }
    public void setVarianceNo(String varianceNo) { this.varianceNo = varianceNo; }
    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getVarianceType() { return varianceType; }
    public void setVarianceType(String varianceType) { this.varianceType = varianceType; }
    public BigDecimal getPlannedQtyKg() { return plannedQtyKg; }
    public void setPlannedQtyKg(BigDecimal plannedQtyKg) { this.plannedQtyKg = plannedQtyKg; }
    public BigDecimal getActualQtyKg() { return actualQtyKg; }
    public void setActualQtyKg(BigDecimal actualQtyKg) { this.actualQtyKg = actualQtyKg; }
    public BigDecimal getDiffQtyKg() { return diffQtyKg; }
    public void setDiffQtyKg(BigDecimal diffQtyKg) { this.diffQtyKg = diffQtyKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
