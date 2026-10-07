package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;

/**
 * 设备登记请求：增氧机/泵站/投饵机，操作者必填。
 */
public class MaintDeviceRegisterRequest {

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;
    @NotBlank(message = "设备名称不能为空")
    private String deviceName;
    @NotBlank(message = "设备类型不能为空（AERATOR/PUMP_STATION/FEEDER）")
    private String deviceType;
    private Long pondId;
    private String seaArea;
    @NotBlank(message = "登记人不能为空")
    private String operator;
    private String remark;

    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceType() { return deviceType; }
    public void setDeviceType(String deviceType) { this.deviceType = deviceType; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
