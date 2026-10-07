package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 分级规则明细：版本下一行一个规格，重量区间半开 [minWeightG, maxWeightG)，行间不重叠。
 */
@TableName("t_fishing_grading_rule_line")
public class FishingGradingRuleLine extends BaseEntity {

    private Long ruleId;
    private String specCode;
    private BigDecimal minWeightG;
    private BigDecimal maxWeightG;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getMinWeightG() { return minWeightG; }
    public void setMinWeightG(BigDecimal minWeightG) { this.minWeightG = minWeightG; }
    public BigDecimal getMaxWeightG() { return maxWeightG; }
    public void setMaxWeightG(BigDecimal maxWeightG) { this.maxWeightG = maxWeightG; }
}
