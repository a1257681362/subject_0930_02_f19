package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 配方规则行：按 苗种 + 日龄区间 + 水温区间 选择饲料与计划投喂量。
 * 随版本快照存在，发布后不改。区间为闭区间；命中多行时取区间最窄（最具体）的一行。
 */
@TableName("t_feed_formula_rule")
public class FeedFormulaRule extends BaseEntity {

    private Long versionId;
    private String species;
    private Integer dayAgeFrom;
    private Integer dayAgeTo;
    private BigDecimal waterTempFrom;
    private BigDecimal waterTempTo;
    private String feedName;
    private BigDecimal plannedQtyKg;
    private Integer sortNo;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
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
