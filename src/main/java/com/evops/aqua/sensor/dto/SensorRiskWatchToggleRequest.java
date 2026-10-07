package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;

/**
 * 离线归并高风险观察开关调整请求。置为观察/解除观察均须登记原因。
 */
public class SensorRiskWatchToggleRequest {

    @NotBlank(message = "调整离线归并高风险观察开关必须登记原因")
    private String reason;

    private String requestId;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
