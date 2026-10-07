package com.evops.aqua.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日常记录请求：按池按批登记水质与死亡情况。
 */
public class DailyRecordRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotNull(message = "投苗批次不能为空")
    private Long batchId;

    @NotNull(message = "记录日期不能为空")
    private LocalDate recordDate;

    private BigDecimal waterDepthM;
    private BigDecimal salinityPpt;
    private BigDecimal waterTempC;

    @Min(value = 0, message = "死亡数量不能为负")
    private Integer mortalityCount;

    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getRecordDate() { return recordDate; }
    public void setRecordDate(LocalDate recordDate) { this.recordDate = recordDate; }
    public BigDecimal getWaterDepthM() { return waterDepthM; }
    public void setWaterDepthM(BigDecimal waterDepthM) { this.waterDepthM = waterDepthM; }
    public BigDecimal getSalinityPpt() { return salinityPpt; }
    public void setSalinityPpt(BigDecimal salinityPpt) { this.salinityPpt = salinityPpt; }
    public BigDecimal getWaterTempC() { return waterTempC; }
    public void setWaterTempC(BigDecimal waterTempC) { this.waterTempC = waterTempC; }
    public Integer getMortalityCount() { return mortalityCount; }
    public void setMortalityCount(Integer mortalityCount) { this.mortalityCount = mortalityCount; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
