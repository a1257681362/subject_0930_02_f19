package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;

/**
 * 设备更换：登记新设备并建立新旧关联，旧设备退役。
 * 更换原因与操作人必填；requestId 幂等，重复请求返回首次更换结果。
 */
public class DeviceReplaceRequest {

    @NotBlank(message = "新设备编码不能为空")
    private String newDeviceCode;
    @NotBlank(message = "新设备名称不能为空")
    private String newDeviceName;
    @NotBlank(message = "新设备签名密钥不能为空")
    private String newSecretKey;
    @NotBlank(message = "更换原因不能为空")
    private String reason;
    @NotBlank(message = "操作人不能为空")
    private String operatorName;
    @NotBlank(message = "请求号不能为空（用于幂等）")
    private String requestId;

    public String getNewDeviceCode() { return newDeviceCode; }
    public void setNewDeviceCode(String newDeviceCode) { this.newDeviceCode = newDeviceCode; }
    public String getNewDeviceName() { return newDeviceName; }
    public void setNewDeviceName(String newDeviceName) { this.newDeviceName = newDeviceName; }
    public String getNewSecretKey() { return newSecretKey; }
    public void setNewSecretKey(String newSecretKey) { this.newSecretKey = newSecretKey; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
