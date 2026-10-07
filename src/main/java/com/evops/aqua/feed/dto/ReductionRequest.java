package com.evops.aqua.feed.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 临时减量登记：实际投喂量调到计划量以下时，必须登记减量原因与审批人。
 */
public class ReductionRequest {

    @NotNull(message = "减量后投喂量不能为空")
    @DecimalMin(value = "0.01", message = "减量后投喂量必须大于0")
    private BigDecimal actualQtyKg;

    @NotBlank(message = "临时减量必须登记原因")
    private String reductionReason;

    @NotBlank(message = "临时减量必须登记审批人")
    private String reductionApprovedBy;

    private String requestId;

    public BigDecimal getActualQtyKg() { return actualQtyKg; }
    public void setActualQtyKg(BigDecimal actualQtyKg) { this.actualQtyKg = actualQtyKg; }
    public String getReductionReason() { return reductionReason; }
    public void setReductionReason(String reductionReason) { this.reductionReason = reductionReason; }
    public String getReductionApprovedBy() { return reductionApprovedBy; }
    public void setReductionApprovedBy(String reductionApprovedBy) {
        this.reductionApprovedBy = reductionApprovedBy;
    }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
