package com.evops.aqua.dto;

import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 批次估计生物量核定请求：转池成本按数量与生物量分配，生物量以最近核定为准。
 */
public class BiomassUpdateRequest {

    @NotNull(message = "生物量不能为空")
    private BigDecimal biomassKg;

    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
}
