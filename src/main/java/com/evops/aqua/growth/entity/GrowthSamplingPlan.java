package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 生长抽样方案：按池塘 + 批次配置抽样数量、测量频次（间隔天数）及允许偏差（百分数）。
 * 建立即冻结当前设备校正系数版本；按池按批按频次在区间内生成测量任务。
 */
@TableName("t_growth_plan")
public class GrowthSamplingPlan extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private String planNo;
    private String planName;
    private Long pondId;
    private Long batchId;
    private Integer sampleCount;
    private Integer measureIntervalDays;
    private BigDecimal allowedDeviationPct;
    private LocalDate startDate;
    private LocalDate endDate;
    private String deviceCode;
    private Long coeffVersionId;
    private String status;
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
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public Long getCoeffVersionId() { return coeffVersionId; }
    public void setCoeffVersionId(Long coeffVersionId) { this.coeffVersionId = coeffVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
