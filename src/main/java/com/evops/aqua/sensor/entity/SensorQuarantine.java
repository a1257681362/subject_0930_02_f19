package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 签名失败/未知设备隔离区：整包隔离并保留原始报文，
 * 便于事后核查，隔离包绝不进入归并。
 */
@TableName("t_sensor_quarantine")
public class SensorQuarantine extends BaseEntity {

    private String deviceCode;
    private String requestId;
    private String signature;
    private String rawPayload;
    private String reason;
    private LocalDateTime receivedTime;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }
    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getReceivedTime() { return receivedTime; }
    public void setReceivedTime(LocalDateTime receivedTime) { this.receivedTime = receivedTime; }
}
