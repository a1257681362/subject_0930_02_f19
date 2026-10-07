package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 每批每类成本余额账：归集增加、转池分出减少/转入增加、出池结转清零。
 */
@TableName("t_cost_balance")
public class CostBalance extends BaseEntity {

    private Long batchId;
    private Long pondId;
    private String costType;
    private Integer quantity;
    private BigDecimal biomassKg;
    private BigDecimal settledAmount;
    private BigDecimal carryoverInAmount;
    private BigDecimal carryoverOutAmount;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public void setSettledAmount(BigDecimal settledAmount) { this.settledAmount = settledAmount; }
    public BigDecimal getCarryoverInAmount() { return carryoverInAmount; }
    public void setCarryoverInAmount(BigDecimal carryoverInAmount) { this.carryoverInAmount = carryoverInAmount; }
    public BigDecimal getCarryoverOutAmount() { return carryoverOutAmount; }
    public void setCarryoverOutAmount(BigDecimal carryoverOutAmount) { this.carryoverOutAmount = carryoverOutAmount; }
}
