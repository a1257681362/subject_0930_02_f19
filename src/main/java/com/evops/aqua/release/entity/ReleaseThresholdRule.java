package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 阈值行：某规则版本下，按「品种 + 用途」组合逐指标配置放行阈值。
 * min/max 构成闭区间，可只配一端；随版本快照，发布后不可改（调整只能发新版本）。
 */
@TableName("t_rel_threshold_rule")
public class ReleaseThresholdRule extends BaseEntity {

    private Long versionId;
    private String speciesCode;
    private String speciesName;
    private String usageCode;
    private String usageName;
    private String indicatorCode;
    private String indicatorName;
    private String unit;
    private BigDecimal minValue;
    private BigDecimal maxValue;
    private Boolean requiredFlag;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getSpeciesCode() { return speciesCode; }
    public void setSpeciesCode(String speciesCode) { this.speciesCode = speciesCode; }
    public String getSpeciesName() { return speciesName; }
    public void setSpeciesName(String speciesName) { this.speciesName = speciesName; }
    public String getUsageCode() { return usageCode; }
    public void setUsageCode(String usageCode) { this.usageCode = usageCode; }
    public String getUsageName() { return usageName; }
    public void setUsageName(String usageName) { this.usageName = usageName; }
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
