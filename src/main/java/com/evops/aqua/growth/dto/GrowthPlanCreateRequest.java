package com.evops.aqua.growth.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建生长抽样方案：按池塘 + 批次配置抽样数量、测量频次及允许偏差。
 * coeffVersionId 为空时取该设备（deviceCode）当前 ACTIVE 校正系数版本并冻结。
 */
public class GrowthPlanCreateRequest {

    @NotBlank(message = "抽样方案编号不能为空")
    @Size(max = 40, message = "抽样方案编号最长40位")
    private String planNo;

    @NotBlank(message = "抽样方案名称不能为空")
    private String planName;

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotNull(message = "养殖批次不能为空")
    private Long batchId;

    @NotNull(message = "抽样数量不能为空")
    @Min(value = 1, message = "抽样数量必须大于0")
    private Integer sampleCount;

    @NotNull(message = "测量频次（间隔天数）不能为空")
    @Min(value = 1, message = "测量间隔天数必须大于0")
    private Integer measureIntervalDays;

    @NotNull(message = "允许偏差不能为空")
    @DecimalMin(value = "0.01", message = "允许偏差必须大于0")
    private BigDecimal allowedDeviationPct;

    @NotBlank(message = "测量设备不能为空")
    private String deviceCode;

    private Long coeffVersionId;

    @NotNull(message = "方案开始日期不能为空")
    private LocalDate startDate;

    @NotNull(message = "方案结束日期不能为空")
    private LocalDate endDate;

    private String remark;

    public String getPlanNo() { return planNo; }
    public void setPlanNo(String planNo) { this.planNo = planNo; }
    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public Integer getMeasureIntervalDays() { return measureIntervalDays; }
    public void setMeasureIntervalDays(Integer measureIntervalDays) { this.measureIntervalDays = measureIntervalDays; }
    public BigDecimal getAllowedDeviationPct() { return allowedDeviationPct; }
    public void setAllowedDeviationPct(BigDecimal allowedDeviationPct) { this.allowedDeviationPct = allowedDeviationPct; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public Long getCoeffVersionId() { return coeffVersionId; }
    public void setCoeffVersionId(Long coeffVersionId) { this.coeffVersionId = coeffVersionId; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
