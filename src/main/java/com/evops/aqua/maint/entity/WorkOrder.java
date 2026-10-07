package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 保养工单：关联计划（临时工单可为空）、停机窗口、维修人员（头表记主修，明细支持多人）。
 * 触发来源 HOURS / CALENDAR / MANUAL。
 * 状态机：PLANNED → ASSIGNED → IN_PROGRESS → COMPLETED → ACCEPTED → CLOSED。
 * 优先级（逾期升级）：NORMAL → WARNING → ESCALATED，只升不降。
 * 未验收（非 ACCEPTED）不能关闭；关闭即设备恢复运行，此前必须全部完成安全确认。
 */
@TableName("t_maint_work_order")
public class WorkOrder extends BaseEntity {

    public static final String TRIGGER_HOURS = "HOURS";
    public static final String TRIGGER_CALENDAR = "CALENDAR";
    public static final String TRIGGER_MANUAL = "MANUAL";

    public static final String STATUS_PLANNED = "PLANNED";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_ACCEPTED = "ACCEPTED";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_WARNING = "WARNING";
    public static final String PRIORITY_ESCALATED = "ESCALATED";

    private String orderNo;
    private Long planId;
    private Long deviceId;
    private String triggerSource;
    private LocalDateTime dueAt;
    private Long downtimeWindowId;
    private LocalDateTime schedStart;
    private LocalDateTime schedEnd;
    private Long leadWorkerId;
    private String priority;
    private String status;
    private Integer planVersionNo;
    private BigDecimal dueReading;
    /** 到期自然键：小时轨=到期读数（H{reading}），日历轨=到期日（D{yyyyMMdd}）；uk(plan,source,token) 保证同轨同期只生成一张。 */
    private String dueToken;
    private String frozenDetail;
    private String acceptedBy;
    private LocalDateTime acceptedAt;
    private String acceptRemark;
    private LocalDateTime escalatedAt;
    private Integer escalateCount;
    private String requestId;
    private LocalDateTime completedAt;
    private LocalDateTime closedAt;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getTriggerSource() { return triggerSource; }
    public void setTriggerSource(String triggerSource) { this.triggerSource = triggerSource; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    public Long getDowntimeWindowId() { return downtimeWindowId; }
    public void setDowntimeWindowId(Long downtimeWindowId) { this.downtimeWindowId = downtimeWindowId; }
    public LocalDateTime getSchedStart() { return schedStart; }
    public void setSchedStart(LocalDateTime schedStart) { this.schedStart = schedStart; }
    public LocalDateTime getSchedEnd() { return schedEnd; }
    public void setSchedEnd(LocalDateTime schedEnd) { this.schedEnd = schedEnd; }
    public Long getLeadWorkerId() { return leadWorkerId; }
    public void setLeadWorkerId(Long leadWorkerId) { this.leadWorkerId = leadWorkerId; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getPlanVersionNo() { return planVersionNo; }
    public void setPlanVersionNo(Integer planVersionNo) { this.planVersionNo = planVersionNo; }
    public BigDecimal getDueReading() { return dueReading; }
    public void setDueReading(BigDecimal dueReading) { this.dueReading = dueReading; }
    public String getDueToken() { return dueToken; }
    public void setDueToken(String dueToken) { this.dueToken = dueToken; }
    public String getFrozenDetail() { return frozenDetail; }
    public void setFrozenDetail(String frozenDetail) { this.frozenDetail = frozenDetail; }
    public String getAcceptedBy() { return acceptedBy; }
    public void setAcceptedBy(String acceptedBy) { this.acceptedBy = acceptedBy; }
    public LocalDateTime getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(LocalDateTime acceptedAt) { this.acceptedAt = acceptedAt; }
    public String getAcceptRemark() { return acceptRemark; }
    public void setAcceptRemark(String acceptRemark) { this.acceptRemark = acceptRemark; }
    public LocalDateTime getEscalatedAt() { return escalatedAt; }
    public void setEscalatedAt(LocalDateTime escalatedAt) { this.escalatedAt = escalatedAt; }
    public Integer getEscalateCount() { return escalateCount; }
    public void setEscalateCount(Integer escalateCount) { this.escalateCount = escalateCount; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
}
