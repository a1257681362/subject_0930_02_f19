package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 设备更换新旧关联：旧设备退役、新设备接续同一池塘。
 * 关联行只追加，可沿 old/new 链双向追溯设备谱系。
 */
@TableName("t_sensor_device_succession")
public class SensorDeviceSuccession extends BaseEntity {

    private Long pondId;
    private Long oldDeviceId;
    private Long newDeviceId;
    private String reason;
    private String operatorName;
    private LocalDateTime replacedTime;
    private String requestId;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getOldDeviceId() { return oldDeviceId; }
    public void setOldDeviceId(Long oldDeviceId) { this.oldDeviceId = oldDeviceId; }
    public Long getNewDeviceId() { return newDeviceId; }
    public void setNewDeviceId(Long newDeviceId) { this.newDeviceId = newDeviceId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getReplacedTime() { return replacedTime; }
    public void setReplacedTime(LocalDateTime replacedTime) { this.replacedTime = replacedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
