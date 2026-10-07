package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 传感器设备档案：绑定池塘与签名密钥。设备更换后旧设备置 RETIRED，
 * 新旧关联见 t_sensor_device_succession。
 */
@TableName("t_sensor_device")
public class SensorDevice extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RETIRED = "RETIRED";

    private String deviceCode;
    private String deviceName;
    private Long pondId;
    private String secretKey;
    private String status;
    private LocalDate installedDate;
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
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getInstalledDate() { return installedDate; }
    public void setInstalledDate(LocalDate installedDate) { this.installedDate = installedDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
