package com.evops.aqua.feed.dto;

import javax.validation.Valid;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 投喂配方版本发布请求：同一苗种的配方调整以新版本发布，旧版本自动停用。
 * 版本下按 日龄区间 + 水温区间 配置饲料与计划投喂量。
 */
public class FormulaPublishRequest {

    @NotBlank(message = "配方版本号不能为空")
    private String versionNo;

    @NotBlank(message = "苗种（品种）不能为空")
    private String species;

    private String remark;

    @NotEmpty(message = "至少配置一条配方规则")
    @Valid
    private List<RuleDto> rules;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<RuleDto> getRules() { return rules; }
    public void setRules(List<RuleDto> rules) { this.rules = rules; }

    /**
     * 配方规则行：日龄闭区间 + 水温闭区间命中一种饲料与计划量。
     */
    public static class RuleDto {
        @NotNull(message = "日龄下限不能为空")
        private Integer dayAgeFrom;
        @NotNull(message = "日龄上限不能为空")
        private Integer dayAgeTo;
        @NotNull(message = "水温下限不能为空")
        private BigDecimal waterTempFrom;
        @NotNull(message = "水温上限不能为空")
        private BigDecimal waterTempTo;
        @NotBlank(message = "饲料名称不能为空")
        private String feedName;
        @NotNull(message = "计划投喂量不能为空")
        @DecimalMin(value = "0.01", message = "计划投喂量必须大于0")
        private BigDecimal plannedQtyKg;
        private Integer sortNo;

        public Integer getDayAgeFrom() { return dayAgeFrom; }
        public void setDayAgeFrom(Integer dayAgeFrom) { this.dayAgeFrom = dayAgeFrom; }
        public Integer getDayAgeTo() { return dayAgeTo; }
        public void setDayAgeTo(Integer dayAgeTo) { this.dayAgeTo = dayAgeTo; }
        public BigDecimal getWaterTempFrom() { return waterTempFrom; }
        public void setWaterTempFrom(BigDecimal waterTempFrom) { this.waterTempFrom = waterTempFrom; }
        public BigDecimal getWaterTempTo() { return waterTempTo; }
        public void setWaterTempTo(BigDecimal waterTempTo) { this.waterTempTo = waterTempTo; }
        public String getFeedName() { return feedName; }
        public void setFeedName(String feedName) { this.feedName = feedName; }
        public BigDecimal getPlannedQtyKg() { return plannedQtyKg; }
        public void setPlannedQtyKg(BigDecimal plannedQtyKg) { this.plannedQtyKg = plannedQtyKg; }
        public Integer getSortNo() { return sortNo; }
        public void setSortNo(Integer sortNo) { this.sortNo = sortNo; }
    }
}
