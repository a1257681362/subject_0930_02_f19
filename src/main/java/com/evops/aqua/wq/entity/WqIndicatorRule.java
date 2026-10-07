package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 指标规则：某阶段下单个指标的正常范围与是否必测。
 * 随版本快照，发布后不再修改（改范围走新版本）。
 */
@TableName("t_wq_indicator_rule")
public class WqIndicatorRule extends BaseEntity {

    private Long stageRuleId;
    private String indicatorCode;
    private String indicatorName;
    private String unit;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private Boolean requiredFlag;

    public Long getStageRuleId() { return stageRuleId; }
    public void setStageRuleId(Long stageRuleId) { this.stageRuleId = stageRuleId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getMinValue() { return minValue; }
    public void setMinValue(BigDecimal minValue) { this.minValue = minValue; }
    public BigDecimal getMaxValue() { return maxValue; }
    public void setMaxValue(BigDecimal maxValue) { this.maxValue = maxValue; }
    public Boolean getRequiredFlag() { return requiredFlag; }
    public void setRequiredFlag(Boolean requiredFlag) { this.requiredFlag = requiredFlag; }
}
