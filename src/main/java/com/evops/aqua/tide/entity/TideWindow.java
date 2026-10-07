package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 换水窗口：由高潮潮汐事件向前后各推 margin 分钟生成候选，
 * 叠加禁作天气（半开区间不重叠）与人员班次（闭区间全覆盖）后形成的可执行窗口。
 * 统一口径：归属日 window_date = 窗口开始时间所在自然日——跨午夜窗口归开始日，
 * 查询、排班、通知全部按此口径归属。一个潮汐事件只生成一个窗口。
 * 状态 OPEN（可执行）/ CLOSED（临时关闭）。
 */
@TableName("t_tide_window")
public class TideWindow extends BaseEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    private String windowNo;
    private String seaArea;
    private Long tideEventId;
    private String crewName;
    private LocalDate windowDate;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String status;
    private String closedBy;
    private LocalDateTime closedTime;
    private String closedReason;
    private String requestId;

    public String getWindowNo() { return windowNo; }
    public void setWindowNo(String windowNo) { this.windowNo = windowNo; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public Long getTideEventId() { return tideEventId; }
    public void setTideEventId(Long tideEventId) { this.tideEventId = tideEventId; }
    public String getCrewName() { return crewName; }
    public void setCrewName(String crewName) { this.crewName = crewName; }
    public LocalDate getWindowDate() { return windowDate; }
    public void setWindowDate(LocalDate windowDate) { this.windowDate = windowDate; }
    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }
    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedTime() { return closedTime; }
    public void setClosedTime(LocalDateTime closedTime) { this.closedTime = closedTime; }
    public String getClosedReason() { return closedReason; }
    public void setClosedReason(String closedReason) { this.closedReason = closedReason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
