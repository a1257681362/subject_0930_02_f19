package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;

/**
 * 临时换车请求：必须登记原因；登记后触发客户通知。
 * requestId 幂等——重试不重复换车、不重复通知。
 */
public class VehicleChangeRequest {

    @NotBlank(message = "新车牌号不能为空")
    private String newVehicleNo;

    @NotBlank(message = "换车原因不能为空")
    private String reason;

    @NotBlank(message = "操作人不能为空")
    private String changedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getNewVehicleNo() { return newVehicleNo; }
    public void setNewVehicleNo(String newVehicleNo) { this.newVehicleNo = newVehicleNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getChangedBy() { return changedBy; }
    public void setChangedBy(String changedBy) { this.changedBy = changedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
