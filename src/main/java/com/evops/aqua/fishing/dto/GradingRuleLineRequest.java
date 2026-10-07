package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 分级规则明细行：规格 + 重量区间（半开 [minWeightG, maxWeightG)）。
 */
public class GradingRuleLineRequest {

    @NotBlank(message = "规格不能为空")
    private String specCode;

    @NotNull(message = "重量区间下限不能为空")
    private BigDecimal minWeightG;

    @NotNull(message = "重量区间上限不能为空")
    private BigDecimal maxWeightG;

    public String getSpecCode() { return specCode; }
    public void setSpecCode(String specCode) { this.specCode = specCode; }
    public BigDecimal getMinWeightG() { return minWeightG; }
    public void setMinWeightG(BigDecimal minWeightG) { this.minWeightG = minWeightG; }
    public BigDecimal getMaxWeightG() { return maxWeightG; }
    public void setMaxWeightG(BigDecimal maxWeightG) { this.maxWeightG = maxWeightG; }
}
