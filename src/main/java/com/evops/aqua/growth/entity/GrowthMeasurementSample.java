package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 现场样本明细：每尾抽样品一行。
 * 校正值 = 原始读数 × 任务冻结的设备系数（weightCoeff/lengthCoeff 随行快照，coeffVersionId 冻结版本）。
 * 异常样本只能置 abnormalityFlag=TRUE 并填写 reviewReason（复核理由）与 reviewedBy（复核人），
 * 绝不允许删除或静默剔除——includedFlag 反映其是否计入均值，异常行永久保留。
 */
@TableName("t_growth_sample")
public class GrowthMeasurementSample extends BaseEntity {

    private Long taskId;
    private Integer seqNo;
    private BigDecimal rawWeightG;
    private BigDecimal rawLengthMm;
    private BigDecimal weightCoeff;
    private BigDecimal lengthCoeff;
    private Long coeffVersionId;
    private BigDecimal correctedWeightG;
    private BigDecimal correctedLengthMm;
    private Boolean abnormalityFlag;
    private String reviewReason;
    private String reviewedBy;
    private LocalDateTime reviewedTime;
    private Boolean includedFlag;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public BigDecimal getRawWeightG() { return rawWeightG; }
    public void setRawWeightG(BigDecimal rawWeightG) { this.rawWeightG = rawWeightG; }
    public BigDecimal getRawLengthMm() { return rawLengthMm; }
    public void setRawLengthMm(BigDecimal rawLengthMm) { this.rawLengthMm = rawLengthMm; }
    public BigDecimal getWeightCoeff() { return weightCoeff; }
    public void setWeightCoeff(BigDecimal weightCoeff) { this.weightCoeff = weightCoeff; }
    public BigDecimal getLengthCoeff() { return lengthCoeff; }
    public void setLengthCoeff(BigDecimal lengthCoeff) { this.lengthCoeff = lengthCoeff; }
    public Long getCoeffVersionId() { return coeffVersionId; }
    public void setCoeffVersionId(Long coeffVersionId) { this.coeffVersionId = coeffVersionId; }
    public BigDecimal getCorrectedWeightG() { return correctedWeightG; }
    public void setCorrectedWeightG(BigDecimal correctedWeightG) { this.correctedWeightG = correctedWeightG; }
    public BigDecimal getCorrectedLengthMm() { return correctedLengthMm; }
    public void setCorrectedLengthMm(BigDecimal correctedLengthMm) { this.correctedLengthMm = correctedLengthMm; }
    public Boolean getAbnormalityFlag() { return abnormalityFlag; }
    public void setAbnormalityFlag(Boolean abnormalityFlag) { this.abnormalityFlag = abnormalityFlag; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedTime() { return reviewedTime; }
    public void setReviewedTime(LocalDateTime reviewedTime) { this.reviewedTime = reviewedTime; }
    public Boolean getIncludedFlag() { return includedFlag; }
    public void setIncludedFlag(Boolean includedFlag) { this.includedFlag = includedFlag; }
}
