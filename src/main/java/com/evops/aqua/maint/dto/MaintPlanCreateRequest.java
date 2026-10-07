package com.evops.aqua.maint.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 保养计划创建请求：一设备一行。运行小时轨与日历轨至少启用一条。
 * 小时轨：intervalHours 间隔小时；日历轨：intervalDays 间隔天 + anchorDate 起算日。
 */
public class MaintPlanCreateRequest {

    @NotBlank(message = "计划编号不能为空")
    private String planNo;
    @NotNull(message = "设备不能为空")
    private Long deviceId;
    @NotBlank(message = "计划名称不能为空")
    private String planName;

    private Boolean hoursEnabled;
    private BigDecimal intervalHours;

    private Boolean calendarEnabled;
    private Integer intervalDays;
    private LocalDate anchorDate;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;
    private String remark;

    /** 计划备件模板：生成工单时复制为工单备件需求行。 */
    @Valid
    private List<PartLine> parts;

    public String getPlanNo() { return planNo; }
    public void setPlanNo(String planNo) { this.planNo = planNo; }
    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
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
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<PartLine> getParts() { return parts; }
    public void setParts(List<PartLine> parts) { this.parts = parts; }

    /** 计划备件模板行：品类 + 数量。 */
    public static class PartLine {
        @NotNull(message = "备件品类不能为空")
        private Long catalogId;
        @NotNull(message = "备件数量不能为空")
        private Integer quantity;

        public Long getCatalogId() { return catalogId; }
        public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
