package com.evops.aqua.growth.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 修改抽样方案：仅名称/区间/频次/抽样数/允许偏差/备注可改；
 * 池塘、批次、设备与冻结的系数版本不可改（变更须新建方案）。已生成任务不受影响。
 */
public class GrowthPlanUpdateRequest {

    private String planName;
    @Min(value = 1, message = "抽样数量必须大于0")
    private Integer sampleCount;
    @Min(value = 1, message = "测量间隔天数必须大于0")
    private Integer measureIntervalDays;
    @DecimalMin(value = "0.01", message = "允许偏差必须大于0")
    private BigDecimal allowedDeviationPct;
    private LocalDate startDate;
    private LocalDate endDate;
    private String remark;

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public Integer getMeasureIntervalDays() { return measureIntervalDays; }
    public void setMeasureIntervalDays(Integer measureIntervalDays) { this.measureIntervalDays = measureIntervalDays; }
    public BigDecimal getAllowedDeviationPct() { return allowedDeviationPct; }
    public void setAllowedDeviationPct(BigDecimal allowedDeviationPct) { this.allowedDeviationPct = allowedDeviationPct; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
