package com.evops.aqua.med.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 药品批号库存：一批一行，两量账。
 * 可用量 = quantityIn - quantityConsumed。
 * 库存扣减只发生在领用审核通过的同一事务内；批号不可删除，用尽置 EXHAUSTED。
 */
@TableName("t_med_batch")
public class MedBatch extends BaseEntity {

    public static final String STATUS_IN_STOCK = "IN_STOCK";
    public static final String STATUS_EXHAUSTED = "EXHAUSTED";

    private String batchNo;
    private Long drugId;
    private String supplier;
    private BigDecimal quantityIn;
    private BigDecimal quantityConsumed;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String status;
    private String requestId;
    private String remark;

    /** 可用库存 = 入库 - 已消耗。 */
    public BigDecimal availableQuantity() {
        BigDecimal consumed = quantityConsumed == null ? BigDecimal.ZERO : quantityConsumed;
        return quantityIn.subtract(consumed);
    }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public Long getDrugId() { return drugId; }
    public void setDrugId(Long drugId) { this.drugId = drugId; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public BigDecimal getQuantityIn() { return quantityIn; }
    public void setQuantityIn(BigDecimal quantityIn) { this.quantityIn = quantityIn; }
    public BigDecimal getQuantityConsumed() { return quantityConsumed; }
    public void setQuantityConsumed(BigDecimal quantityConsumed) { this.quantityConsumed = quantityConsumed; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
