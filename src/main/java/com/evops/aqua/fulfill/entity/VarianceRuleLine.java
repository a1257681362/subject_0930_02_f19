package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 净重差异规则行：按规格配置。
 * 超差阈值 = 入库净重 × tolerancePct% + toleranceAbsKg；交接净重差异绝对值超过阈值则冻结该箱。
 */
@TableName("t_fulfill_variance_rule_line")
public class VarianceRuleLine extends BaseEntity {

    private Long ruleId;
    private String specCode;
    private BigDecimal tolerancePct;
    private BigDecimal toleranceAbsKg;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getTolerancePct() { return tolerancePct; }
    public void setTolerancePct(BigDecimal tolerancePct) { this.tolerancePct = tolerancePct; }
    public BigDecimal getToleranceAbsKg() { return toleranceAbsKg; }
    public void setToleranceAbsKg(BigDecimal toleranceAbsKg) { this.toleranceAbsKg = toleranceAbsKg; }
}
