package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 任务测量项：每指标一行。提交时已测必须有值，缺测必须有原因（服务层校验）。
 * value_text 为首次采样原值，补录只写入 t_wq_backfill，不覆盖本行。
 */
@TableName("t_wq_task_item")
public class WqTaskItem extends BaseEntity {

    private Long taskId;
    private String indicatorCode;
    private String indicatorName;
    private String unit;
    private BigDecimal normalMin;
    private BigDecimal normalMax;
    private Boolean requiredFlag;
    private String valueText;
    private Boolean missingFlag;
    private String missingReason;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getNormalMin() { return normalMin; }
    public void setNormalMin(BigDecimal normalMin) { this.normalMin = normalMin; }
    public BigDecimal getNormalMax() { return normalMax; }
    public void setNormalMax(BigDecimal normalMax) { this.normalMax = normalMax; }
    public Boolean getRequiredFlag() { return requiredFlag; }
    public void setRequiredFlag(Boolean requiredFlag) { this.requiredFlag = requiredFlag; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
    public Boolean getMissingFlag() { return missingFlag; }
    public void setMissingFlag(Boolean missingFlag) { this.missingFlag = missingFlag; }
    public String getMissingReason() { return missingReason; }
    public void setMissingReason(String missingReason) { this.missingReason = missingReason; }
}
