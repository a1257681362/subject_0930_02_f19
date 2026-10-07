package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 捕捞现场分仓登记：实际均重用于按当时生效分级规则判实际规格，入对应规格库位。
 * requestId 强制幂等——补录/重试只形成一条可用记录，不重复加账。
 */
public class LoadRecordRequest {

    @NotNull(message = "实际均重不能为空")
    private BigDecimal actualAvgWeightG;

    @NotNull(message = "入仓重量不能为空")
    private BigDecimal weightKg;

    @NotBlank(message = "现场记录人不能为空")
    private String loadedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public BigDecimal getActualAvgWeightG() { return actualAvgWeightG; }
    public void setActualAvgWeightG(BigDecimal actualAvgWeightG) { this.actualAvgWeightG = actualAvgWeightG; }
    public BigDecimal getWeightKg() { return weightKg; }
    public void setWeightKg(BigDecimal weightKg) { this.weightKg = weightKg; }
    public String getLoadedBy() { return loadedBy; }
    public void setLoadedBy(String loadedBy) { this.loadedBy = loadedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
