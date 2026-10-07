package com.evops.aqua.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 投苗批次（原始投苗单）：苗种来源、入池数量、计划出池日。
 * 原始投苗单不可删除——应用层不提供删除入口。
 * biomassKg 为批次当前估计生物量，是转池成本分配的权重之一。
 * splitParentBatchId 非空表示该批由父批次分池自动派生。
 */
@TableName("t_seedling_batch")
public class SeedlingBatch extends BaseEntity {

    public static final String STATUS_STOCKED = "STOCKED";
    public static final String STATUS_HARVESTED = "HARVESTED";

    private String batchNo;
    private Long pondId;
    private String species;
    private String seedSource;
    private Integer quantity;
    private BigDecimal biomassKg;
    private LocalDate stockDate;
    private LocalDate plannedHarvestDate;
    private String status;
    private Long splitParentBatchId;
    private String remark;

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public String getSpecies() { return species; }
    public void setSpecies(String species) { this.species = species; }
    public String getSeedSource() { return seedSource; }
    public void setSeedSource(String seedSource) { this.seedSource = seedSource; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public BigDecimal getBiomassKg() { return biomassKg; }
    public void setBiomassKg(BigDecimal biomassKg) { this.biomassKg = biomassKg; }
    public LocalDate getStockDate() { return stockDate; }
    public void setStockDate(LocalDate stockDate) { this.stockDate = stockDate; }
    public LocalDate getPlannedHarvestDate() { return plannedHarvestDate; }
    public void setPlannedHarvestDate(LocalDate plannedHarvestDate) { this.plannedHarvestDate = plannedHarvestDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getSplitParentBatchId() { return splitParentBatchId; }
    public void setSplitParentBatchId(Long splitParentBatchId) { this.splitParentBatchId = splitParentBatchId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
