package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 停机窗口：设备允许停机的时段，半开区间 [windowStart, windowEnd)。
 * 保养工单必须落入设备的开放窗口；窗口被工单占用期间不可重复预约同一时段。
 */
@TableName("t_maint_downtime_window")
public class DowntimeWindow extends BaseEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    private String windowNo;
    private Long deviceId;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String reason;
    private String status;
    private String registeredBy;
    private String requestId;

    public String getWindowNo() { return windowNo; }
    public void setWindowNo(String windowNo) { this.windowNo = windowNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRegisteredBy() { return registeredBy; }
    public void setRegisteredBy(String registeredBy) { this.registeredBy = registeredBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
