package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 换水设备登记请求：设备编号全局唯一。
 * 同一设备同一时间只能服务一口池（排班时按半开区间判定冲突）。
 */
public class DeviceRequest {

    @NotBlank(message = "设备编号不能为空")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotBlank(message = "所属海区不能为空")
    private String seaArea;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    private String remark;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
