package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 传感器设备登记。requestId 为强制幂等号：重复登记返回首次记录。
 */
public class DeviceRegisterRequest {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;
    @NotBlank(message = "设备名称不能为空")
    private String deviceName;
    @NotNull(message = "所属池塘不能为空")
    private Long pondId;
    @NotBlank(message = "签名密钥不能为空")
    private String secretKey;
    @NotNull(message = "安装日期不能为空")
    private LocalDate installedDate;
    @NotBlank(message = "请求号不能为空（用于幂等）")
    private String requestId;
    private String remark;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public LocalDate getInstalledDate() { return installedDate; }
    public void setInstalledDate(LocalDate installedDate) { this.installedDate = installedDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
