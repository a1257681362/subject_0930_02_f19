package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 养殖设备档案：增氧机 AERATOR / 泵站 PUMP_STATION / 投饵机 FEEDER。
 * 状态 RUNNING（运行中）/ DOWN（停机：保养或故障）。开工即 DOWN，安全确认通过关闭工单才恢复 RUNNING。
 */
@TableName("t_maint_device")
public class MaintDevice extends BaseEntity {

    public static final String TYPE_AERATOR = "AERATOR";
    public static final String TYPE_PUMP_STATION = "PUMP_STATION";
    public static final String TYPE_FEEDER = "FEEDER";

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_DOWN = "DOWN";

    private String deviceCode;
    private String deviceName;
    private String deviceType;
    private Long pondId;
    private String seaArea;
    private BigDecimal runningHours;
    private String status;
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
    public BigDecimal getRunningHours() { return runningHours; }
    public void setRunningHours(BigDecimal runningHours) { this.runningHours = runningHours; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
