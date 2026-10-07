package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 出池结转快照（头）：出池时一张，冻结总金额与逐类明细，不可变。
 */
@TableName("t_cost_carryover")
public class CostCarryover extends BaseEntity {

    private String carryoverNo;
    private Long batchId;
    private Long pondId;
    private String pondStatusAtCarryover;
    private Long harvestRecordId;
    private LocalDate harvestDate;
    private Integer quantity;
    private BigDecimal biomassKg;
    private BigDecimal totalAmount;
    private String snapshotJson;
    private String requestId;

    public String getCarryoverNo() { return carryoverNo; }
    public void setCarryoverNo(String carryoverNo) { this.carryoverNo = carryoverNo; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getPondStatusAtCarryover() { return pondStatusAtCarryover; }
    public void setPondStatusAtCarryover(String pondStatusAtCarryover) { this.pondStatusAtCarryover = pondStatusAtCarryover; }
    public Long getHarvestRecordId() { return harvestRecordId; }
    public void setHarvestRecordId(Long harvestRecordId) { this.harvestRecordId = harvestRecordId; }
    public LocalDate getHarvestDate() { return harvestDate; }
    public void setHarvestDate(LocalDate harvestDate) { this.harvestDate = harvestDate; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
