package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 出池结转快照行：每费用类型一行，冻结余额与累计转入/分出及凭条数。
 */
@TableName("t_cost_carryover_line")
public class CostCarryoverLine extends BaseEntity {

    private Long carryoverId;
    private String costType;
    private BigDecimal settledAmount;
    private BigDecimal carryoverInAmount;
    private BigDecimal carryoverOutAmount;
    private Integer entryCount;

    public Long getCarryoverId() { return carryoverId; }
    public void setCarryoverId(Long carryoverId) { this.carryoverId = carryoverId; }
    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public void setSettledAmount(BigDecimal settledAmount) { this.settledAmount = settledAmount; }
    public BigDecimal getCarryoverInAmount() { return carryoverInAmount; }
    public void setCarryoverInAmount(BigDecimal carryoverInAmount) { this.carryoverInAmount = carryoverInAmount; }
    public BigDecimal getCarryoverOutAmount() { return carryoverOutAmount; }
    public void setCarryoverOutAmount(BigDecimal carryoverOutAmount) { this.carryoverOutAmount = carryoverOutAmount; }
    public Integer getEntryCount() { return entryCount; }
    public void setEntryCount(Integer entryCount) { this.entryCount = entryCount; }
}
