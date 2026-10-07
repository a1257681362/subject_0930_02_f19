package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 保养计划调整请求：间隔/启用开关/起算日可改。
 * 调整只影响尚未生成的工单——已经生成的工单冻结旧版本快照，绝不回改。
 * 每次调整 version_no 递增，调整人与原因必填并留证。
 */
public class PlanAdjustRequest {

    private Boolean hoursEnabled;
    private BigDecimal intervalHours;

    private Boolean calendarEnabled;
    private Integer intervalDays;
    private LocalDate anchorDate;

    @NotBlank(message = "调整人不能为空")
    private String operator;
    @NotBlank(message = "调整原因不能为空")
    private String reason;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public Boolean getHoursEnabled() { return hoursEnabled; }
    public void setHoursEnabled(Boolean hoursEnabled) { this.hoursEnabled = hoursEnabled; }
    public BigDecimal getIntervalHours() { return intervalHours; }
    public void setIntervalHours(BigDecimal intervalHours) { this.intervalHours = intervalHours; }
    public Boolean getCalendarEnabled() { return calendarEnabled; }
    public void setCalendarEnabled(Boolean calendarEnabled) { this.calendarEnabled = calendarEnabled; }
    public Integer getIntervalDays() { return intervalDays; }
    public void setIntervalDays(Integer intervalDays) { this.intervalDays = intervalDays; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public void setAnchorDate(LocalDate anchorDate) { this.anchorDate = anchorDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
