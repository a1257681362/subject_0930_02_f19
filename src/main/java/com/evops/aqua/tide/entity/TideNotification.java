package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 窗口临时关闭通知：发给受影响班组，班组签收留回执（签收人/签收时间/回执备注）。
 * 每个窗口每条排班只发一条，重复关闭不多发；重复签收幂等返回同一回执。
 * 状态 SENT（已发送）/ ACKED（已签收回执）。
 */
@TableName("t_tide_notification")
public class TideNotification extends BaseEntity {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_ACKED = "ACKED";

    private String noticeNo;
    private Long windowId;
    private Long scheduleId;
    private String crewName;
    private String message;
    private String status;
    private String ackBy;
    private LocalDateTime ackTime;
    private String ackRemark;
    private String requestId;

    public String getNoticeNo() { return noticeNo; }
    public void setNoticeNo(String noticeNo) { this.noticeNo = noticeNo; }
    public Long getWindowId() { return windowId; }
    public void setWindowId(Long windowId) { this.windowId = windowId; }
    public Long getScheduleId() { return scheduleId; }
    public void setScheduleId(Long scheduleId) { this.scheduleId = scheduleId; }
    public String getCrewName() { return crewName; }
    public void setCrewName(String crewName) { this.crewName = crewName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAckBy() { return ackBy; }
    public void setAckBy(String ackBy) { this.ackBy = ackBy; }
    public LocalDateTime getAckTime() { return ackTime; }
    public void setAckTime(LocalDateTime ackTime) { this.ackTime = ackTime; }
    public String getAckRemark() { return ackRemark; }
    public void setAckRemark(String ackRemark) { this.ackRemark = ackRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
