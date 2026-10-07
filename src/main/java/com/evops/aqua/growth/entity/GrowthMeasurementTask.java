package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 生长测量任务（现场原单）：按方案按池按批按测量日生成，冻结抽样数、允许偏差与设备系数版本。
 * DRAFT 草稿（可反复暂存）→ SUBMITTED 已提交 → CONFIRMED 主管确认（更新预测出池量）
 *                                          → REJECTED 驳回（可重录重提）。
 * 均值在每次录入后按"未标记异常的样本"重算本任务自身；任务一旦 CONFIRMED 冻结，不再随新系数重算。
 */
@TableName("t_growth_task")
public class GrowthMeasurementTask extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_REJECTED = "REJECTED";

    private String taskNo;
    private Long planId;
    private Long pondId;
    private Long batchId;
    private LocalDate measureDate;
    private Integer sampleCount;
    private BigDecimal allowedDeviationPct;
    private String deviceCode;
    private Long coeffVersionId;
    private String measuredBy;
    private BigDecimal rawAvgWeightG;
    private BigDecimal rawAvgLengthMm;
    private BigDecimal correctedAvgWeightG;
    private BigDecimal correctedAvgLengthMm;
    private BigDecimal predictedHarvestKg;
    private LocalDate predictedHarvestDate;
    private Integer totalCount;
    private String status;
    private String submittedBy;
    private LocalDateTime submittedTime;
    private String confirmedBy;
    private LocalDateTime confirmedTime;
    private String confirmRemark;
    private String rejectRemark;
    private String requestId;
    private String remark;

    public String getTaskNo() { return taskNo; }
    public void setTaskNo(String taskNo) { this.taskNo = taskNo; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long planId) { this.planId = planId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getMeasureDate() { return measureDate; }
    public void setMeasureDate(LocalDate measureDate) { this.measureDate = measureDate; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public BigDecimal getAllowedDeviationPct() { return allowedDeviationPct; }
    public void setAllowedDeviationPct(BigDecimal allowedDeviationPct) { this.allowedDeviationPct = allowedDeviationPct; }
    public String getDeviceCode() { return deviceCode; }
    public void setDeviceCode(String deviceCode) { this.deviceCode = deviceCode; }
    public Long getCoeffVersionId() { return coeffVersionId; }
    public void setCoeffVersionId(Long coeffVersionId) { this.coeffVersionId = coeffVersionId; }
    public String getMeasuredBy() { return measuredBy; }
    public void setMeasuredBy(String measuredBy) { this.measuredBy = measuredBy; }
    public BigDecimal getRawAvgWeightG() { return rawAvgWeightG; }
    public void setRawAvgWeightG(BigDecimal rawAvgWeightG) { this.rawAvgWeightG = rawAvgWeightG; }
    public BigDecimal getRawAvgLengthMm() { return rawAvgLengthMm; }
    public void setRawAvgLengthMm(BigDecimal rawAvgLengthMm) { this.rawAvgLengthMm = rawAvgLengthMm; }
    public BigDecimal getCorrectedAvgWeightG() { return correctedAvgWeightG; }
    public void setCorrectedAvgWeightG(BigDecimal correctedAvgWeightG) { this.correctedAvgWeightG = correctedAvgWeightG; }
    public BigDecimal getCorrectedAvgLengthMm() { return correctedAvgLengthMm; }
    public void setCorrectedAvgLengthMm(BigDecimal correctedAvgLengthMm) { this.correctedAvgLengthMm = correctedAvgLengthMm; }
    public BigDecimal getPredictedHarvestKg() { return predictedHarvestKg; }
    public void setPredictedHarvestKg(BigDecimal predictedHarvestKg) { this.predictedHarvestKg = predictedHarvestKg; }
    public LocalDate getPredictedHarvestDate() { return predictedHarvestDate; }
    public void setPredictedHarvestDate(LocalDate predictedHarvestDate) { this.predictedHarvestDate = predictedHarvestDate; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }
    public LocalDateTime getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(LocalDateTime submittedTime) { this.submittedTime = submittedTime; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(LocalDateTime confirmedTime) { this.confirmedTime = confirmedTime; }
    public String getConfirmRemark() { return confirmRemark; }
    public void setConfirmRemark(String confirmRemark) { this.confirmRemark = confirmRemark; }
    public String getRejectRemark() { return rejectRemark; }
    public void setRejectRemark(String rejectRemark) { this.rejectRemark = rejectRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
