package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;

/**
 * 预约锁定请求：可指定库位，缺省按规格自动挑选空闲库位；
 * 锁定后库位被本订单占用，其他订单不得占用。可同时登记运输车辆。
 */
public class ReservationLockRequest {

    private Long binId;

    private String vehicleNo;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public Long getBinId() { return binId; }
    public void setBinId(Long binId) { this.binId = binId; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
