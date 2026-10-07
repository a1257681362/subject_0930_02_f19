package com.evops.aqua.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 日常记录：按池按批登记水质与死亡情况。
 */
@TableName("t_daily_record")
public class DailyRecord extends BaseEntity {

    private Long pondId;
    private Long batchId;
    private LocalDate recordDate;
    private BigDecimal waterDepthM;
    private BigDecimal salinityPpt;
    private BigDecimal waterTempC;
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
