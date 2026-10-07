package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 捕捞预约：客户订单按「规格+重量区间+交付时间」拆分的最小执行单元。
 * 状态 DRAFT（待锁定）→ LOCKED（已锁定占用库位）→ COMPLETED（已完成，差异已结清）。
 * 锁定后占用库位，其他订单的预约不得占用同一库位。
 */
@TableName("t_fishing_reservation")
public class FishingReservation extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private String resvNo;
    private Long orderId;
    private String specCode;
    private BigDecimal weightMinG;
    private BigDecimal weightMaxG;
    private LocalDateTime deliveryTime;
    private BigDecimal plannedQtyKg;
    private BigDecimal actualQtyKg;
    private Long binId;
    private String vehicleNo;
    private String status;
    private String lockedBy;
    private LocalDateTime lockedTime;
    private LocalDateTime completedTime;
    private String requestId;
    private String remark;

    public String getResvNo() { return resvNo; }
    public void setResvNo(String resvNo) { this.resvNo = resvNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
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
    public BigDecimal getActualQtyKg() { return actualQtyKg; }
    public void setActualQtyKg(BigDecimal actualQtyKg) { this.actualQtyKg = actualQtyKg; }
    public Long getBinId() { return binId; }
    public void setBinId(Long binId) { this.binId = binId; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getLockedBy() { return lockedBy; }
    public void setLockedBy(String lockedBy) { this.lockedBy = lockedBy; }
    public LocalDateTime getLockedTime() { return lockedTime; }
    public void setLockedTime(LocalDateTime lockedTime) { this.lockedTime = lockedTime; }
    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
