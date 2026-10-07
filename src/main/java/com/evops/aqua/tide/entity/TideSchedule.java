package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 排班：换水窗口 + 池塘 + 设备。同一设备同一时间只能服务一口池（半开区间判定）。
 * 状态 PLANNED（待发布）/ PUBLISHED（已发布）/ POSTPONED（已顺延）。
 * 发布后修改必须登记调度审批人与审批意见；
 * 自动顺延只生成新排班行（origin_schedule_id 链接原计划），
 * 原计划行的时间/池塘/设备一律不变，仅状态置 POSTPONED。
 */
@TableName("t_tide_schedule")
public class TideSchedule extends BaseEntity {

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_POSTPONED = "POSTPONED";

    private String scheduleNo;
    private Long windowId;
    private Long pondId;
    private Long deviceId;
    private String seaArea;
    private String crewName;
    private LocalDate schedDate;
    private LocalDateTime schedStart;
    private LocalDateTime schedEnd;
    private Long originScheduleId;
    private String status;
    private String publishedBy;
    private LocalDateTime publishedTime;
    private String requestId;
    private String remark;

    public String getScheduleNo() { return scheduleNo; }
    public void setScheduleNo(String scheduleNo) { this.scheduleNo = scheduleNo; }
    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getCrewName() { return crewName; }
    public void setCrewName(String crewName) { this.crewName = crewName; }
    public LocalDate getSchedDate() { return schedDate; }
    public void setSchedDate(LocalDate schedDate) { this.schedDate = schedDate; }
    public LocalDateTime getSchedStart() { return schedStart; }
    public void setSchedStart(LocalDateTime schedStart) { this.schedStart = schedStart; }
    public LocalDateTime getSchedEnd() { return schedEnd; }
    public void setSchedEnd(LocalDateTime schedEnd) { this.schedEnd = schedEnd; }
    public Long getOriginScheduleId() { return originScheduleId; }
    public void setOriginScheduleId(Long originScheduleId) { this.originScheduleId = originScheduleId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedTime() { return publishedTime; }
    public void setPublishedTime(LocalDateTime publishedTime) { this.publishedTime = publishedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
