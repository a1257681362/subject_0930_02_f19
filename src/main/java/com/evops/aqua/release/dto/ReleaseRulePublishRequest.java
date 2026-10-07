package com.evops.aqua.release.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

/**
 * 发布出池检验阈值新版本：检验项目按「品种 + 用途」组合逐指标配置阈值。
 * 新版本发布后旧版本自动停用；已建立的检验单冻结建单时版本，不受新版本影响。
 */
public class ReleaseRulePublishRequest {

    @NotBlank(message = "规则版本号不能为空")
    private String versionNo;

    private String remark;

    @NotEmpty(message = "至少配置一条品种+用途阈值组合")
    @Valid
    private List<ThresholdDto> thresholds;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<ThresholdDto> getThresholds() { return thresholds; }
    public void setThresholds(List<ThresholdDto> thresholds) { this.thresholds = thresholds; }

    /** 一条 = 某品种 + 某用途下的某个检验指标阈值 */
    public static class ThresholdDto {
        @NotBlank(message = "品种编码不能为空")
        private String speciesCode;
        @NotBlank(message = "品种名称不能为空")
        private String speciesName;
        @NotBlank(message = "用途编码不能为空")
        private String usageCode;
        @NotBlank(message = "用途名称不能为空")
        private String usageName;
        @NotBlank(message = "指标编码不能为空")
        private String indicatorCode;
        @NotBlank(message = "指标名称不能为空")
        private String indicatorName;
        private String unit;
        private BigDecimal minValue;
        private BigDecimal maxValue;
        private Boolean requiredFlag;

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
}
