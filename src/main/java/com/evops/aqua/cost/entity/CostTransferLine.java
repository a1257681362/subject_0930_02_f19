package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 转池分配行：来源批 × 接收批 × 费用类型一行，权重与分配额冻结。
 */
@TableName("t_cost_transfer_line")
public class CostTransferLine extends BaseEntity {

    private Long transferId;
    private Long sourceBatchId;
    private Long receiveBatchId;
    private Long receivePondId;
    private String costType;
    private Integer quantity;
    private BigDecimal biomassKg;
    private BigDecimal weight;
    private BigDecimal allocatedAmount;

    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Long getSourceBatchId() { return sourceBatchId; }
    public void setSourceBatchId(Long sourceBatchId) { this.sourceBatchId = sourceBatchId; }
    public Long getReceiveBatchId() { return receiveBatchId; }
    public void setReceiveBatchId(Long receiveBatchId) { this.receiveBatchId = receiveBatchId; }
    public Long getReceivePondId() { return receivePondId; }
    public void setReceivePondId(Long receivePondId) { this.receivePondId = receivePondId; }
    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }
    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }
}
