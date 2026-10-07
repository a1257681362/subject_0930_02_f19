package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预测出池量结果：每测量任务一条，只有主管确认时才写入。
 * 预测口径冻结在任务当时（均值、系数版本、存活/总数量）；后续发布新系数或产生新测量任务，
 * 均不回头重算历史任务的预测——历史不随新系数重算。
 */
@TableName("t_growth_harvest_forecast")
public class GrowthHarvestForecast extends BaseEntity {

    private Long taskId;
    private Long pondId;
    private Long batchId;
    private LocalDate measureDate;
    private BigDecimal avgWeightG;
    private BigDecimal avgLengthMm;
    private BigDecimal survivalRatePct;
    private Integer totalCount;
    private BigDecimal predictedHarvestKg;
    private LocalDate predictedHarvestDate;
    private Long coeffVersionId;
    private String confirmedBy;
    private LocalDateTime confirmedTime;
    private String confirmRemark;
    private String requestId;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getMeasureDate() { return measureDate; }
    public void setMeasureDate(LocalDate measureDate) { this.measureDate = measureDate; }
    public BigDecimal getAvgWeightG() { return avgWeightG; }
    public void setAvgWeightG(BigDecimal avgWeightG) { this.avgWeightG = avgWeightG; }
    public BigDecimal getAvgLengthMm() { return avgLengthMm; }
    public void setAvgLengthMm(BigDecimal avgLengthMm) { this.avgLengthMm = avgLengthMm; }
    public BigDecimal getSurvivalRatePct() { return survivalRatePct; }
    public void setSurvivalRatePct(BigDecimal survivalRatePct) { this.survivalRatePct = survivalRatePct; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public BigDecimal getPredictedHarvestKg() { return predictedHarvestKg; }
    public void setPredictedHarvestKg(BigDecimal predictedHarvestKg) { this.predictedHarvestKg = predictedHarvestKg; }
    public LocalDate getPredictedHarvestDate() { return predictedHarvestDate; }
    public void setPredictedHarvestDate(LocalDate predictedHarvestDate) { this.predictedHarvestDate = predictedHarvestDate; }
    public Long getCoeffVersionId() { return coeffVersionId; }
    public void setCoeffVersionId(Long coeffVersionId) { this.coeffVersionId = coeffVersionId; }
    public String getConfirmedBy() { return confirmedBy; }
    public void setConfirmedBy(String confirmedBy) { this.confirmedBy = confirmedBy; }
    public LocalDateTime getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(LocalDateTime confirmedTime) { this.confirmedTime = confirmedTime; }
    public String getConfirmRemark() { return confirmRemark; }
    public void setConfirmRemark(String confirmRemark) { this.confirmRemark = confirmRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
