package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 池塘健康曲线点：只有主管审核通过的已测值才会进入。
 * 每任务每指标仅一条（uk），补录不改写已入曲线的值。
 */
@TableName("t_wq_health_point")
public class WqHealthPoint extends BaseEntity {

    private Long pondId;
    private Long taskId;
    private String indicatorCode;
    private String indicatorName;
    private String unit;
    private LocalDate sampleDate;
    private BigDecimal sampleValue;
    private Long taskRuleVersionId;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public LocalDate getSampleDate() { return sampleDate; }
    public void setSampleDate(LocalDate sampleDate) { this.sampleDate = sampleDate; }
    public BigDecimal getSampleValue() { return sampleValue; }
    public void setSampleValue(BigDecimal sampleValue) { this.sampleValue = sampleValue; }
    public Long getTaskRuleVersionId() { return taskRuleVersionId; }
    public void setTaskRuleVersionId(Long taskRuleVersionId) { this.taskRuleVersionId = taskRuleVersionId; }
}
