package com.evops.aqua.wq.dto;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 发布指标规则新版本：按养殖阶段配置采样频率与指标正常范围。
 * 新版本发布后旧版本自动停用，且只对未开始（未生成）的任务生效。
 */
public class RuleVersionPublishRequest {

    @NotBlank(message = "规则版本号不能为空")
    private String versionNo;

    private String remark;

    @NotEmpty(message = "至少配置一个养殖阶段")
    @Valid
    private List<StageDto> stages;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<StageDto> getStages() { return stages; }
    public void setStages(List<StageDto> stages) { this.stages = stages; }

    /**
     * 养殖阶段配置：采样频率（间隔天数）+ 指标范围。
     */
    public static class StageDto {
        @NotBlank(message = "养殖阶段编码不能为空")
        private String stageCode;
        @NotBlank(message = "养殖阶段名称不能为空")
        private String stageName;
        @NotNull(message = "采样间隔天数不能为空")
        @Min(value = 1, message = "采样间隔天数至少为1")
        private Integer sampleIntervalDays;
        private Integer sortNo;
        @NotEmpty(message = "阶段至少配置一个指标")
        @Valid
        private List<IndicatorDto> indicators;

        public String getStageCode() { return stageCode; }
        public void setStageCode(String stageCode) { this.stageCode = stageCode; }
        public String getStageName() { return stageName; }
        public void setStageName(String stageName) { this.stageName = stageName; }
        public Integer getSampleIntervalDays() { return sampleIntervalDays; }
        public void setSampleIntervalDays(Integer sampleIntervalDays) { this.sampleIntervalDays = sampleIntervalDays; }
        public Integer getSortNo() { return sortNo; }
        public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
        public List<IndicatorDto> getIndicators() { return indicators; }
        public void setIndicators(List<IndicatorDto> indicators) { this.indicators = indicators; }
    }

    /**
     * 指标正常范围：min/max 可只填一端；两端都填时最小值不能大于最大值。
     */
    public static class IndicatorDto {
        @NotBlank(message = "指标编码不能为空")
        private String indicatorCode;
        @NotBlank(message = "指标名称不能为空")
        private String indicatorName;
        private String unit;
        private BigDecimal minValue;
        private BigDecimal maxValue;
        private Boolean requiredFlag;

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
