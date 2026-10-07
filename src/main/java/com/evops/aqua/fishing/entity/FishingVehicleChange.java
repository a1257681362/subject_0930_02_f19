package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 临时换车记录：原因必填；每次换车触发一条客户通知。
 * requestId 唯一——重试不会重复换车、不会重复通知。
 */
@TableName("t_fishing_vehicle_change")
public class FishingVehicleChange extends BaseEntity {

    private Long reservationId;
    private Long orderId;
    private String oldVehicleNo;
    private String newVehicleNo;
    private String reason;
    private String changedBy;
    private String requestId;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOldVehicleNo() { return oldVehicleNo; }
    public void setOldVehicleNo(String oldVehicleNo) { this.oldVehicleNo = oldVehicleNo; }
    public String getNewVehicleNo() { return newVehicleNo; }
    public void setNewVehicleNo(String newVehicleNo) { this.newVehicleNo = newVehicleNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
