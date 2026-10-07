package com.evops.aqua.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 换池请求。
 * 不传 quantity（或数量等于批次在养数量）时按整批转移，历史台账行为不变；
 * 传小于在养数量时为分池：源批次核减数量并在目标池派生接收批次，
 * 成本模块据此按数量与生物量权重分配未结成本。
 * biomassKg 为转出部分生物量（kg）；分池且不传时按数量比例从源批次生物量折算。
 */
public class TransferRequest {

    @NotNull(message = "目标养殖池不能为空")
    private Long toPondId;

    @NotNull(message = "换池日期不能为空")
    private LocalDate transferDate;

    @Min(value = 1, message = "转池数量必须大于0")
    private Integer quantity;

    @Min(value = 0, message = "转出生物量不能为负")
    private BigDecimal biomassKg;

    private String reason;

    public Long getToPondId() { return toPondId; }
    public void setToPondId(Long toPondId) { this.toPondId = toPondId; }
    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
