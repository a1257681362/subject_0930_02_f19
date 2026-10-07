package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保养计划：一设备一行，运行小时（HOURS）与日历（CALENDAR）双轨触发，可只启用其一或同时启用。
 * 两轨各自记录上次生成工单时的基准：last_generated_reading（小时读数）/ last_generated_date（基准日期）。
 * 计划调整（间隔/启用开关）只影响尚未生成的工单——已生成工单冻结 version_no 快照，永不回改。
 */
@TableName("t_maint_plan")
public class MaintPlan extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_PAUSED = "PAUSED";

    private String planNo;
    private Long deviceId;
    private String planName;
    private Integer hoursEnabled;
    private BigDecimal intervalHours;
    private BigDecimal lastGeneratedReading;
    private Integer calendarEnabled;
    private Integer intervalDays;
    private LocalDate anchorDate;
    private LocalDate lastGeneratedDate;
    private Integer versionNo;
    private String status;
    private String requestId;
    private String remark;

    public String getPlanNo() { return planNo; }
    public void setPlanNo(String planNo) { this.planNo = planNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Integer getHoursEnabled() { return hoursEnabled; }
    public void setHoursEnabled(Integer hoursEnabled) { this.hoursEnabled = hoursEnabled; }
    public BigDecimal getIntervalHours() { return intervalHours; }
    public void setIntervalHours(BigDecimal intervalHours) { this.intervalHours = intervalHours; }
    public BigDecimal getLastGeneratedReading() { return lastGeneratedReading; }
    public void setLastGeneratedReading(BigDecimal lastGeneratedReading) { this.lastGeneratedReading = lastGeneratedReading; }
    public Integer getCalendarEnabled() { return calendarEnabled; }
    public void setCalendarEnabled(Integer calendarEnabled) { this.calendarEnabled = calendarEnabled; }
    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }
    public LocalDate getLastGeneratedDate() { return lastGeneratedDate; }
    public void setLastGeneratedDate(LocalDate lastGeneratedDate) { this.lastGeneratedDate = lastGeneratedDate; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
