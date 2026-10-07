package com.evops.aqua.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 出池请求：生成出池记录，批次状态置为已出池。
 */
public class HarvestRequest {

    @NotNull(message = "出池日期不能为空")
    private LocalDate harvestDate;

    @NotNull(message = "出池数量不能为空")
    @Min(value = 1, message = "出池数量必须大于0")
    private Integer quantity;

    private String remark;

    public LocalDate getHarvestDate() { return harvestDate; }
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
