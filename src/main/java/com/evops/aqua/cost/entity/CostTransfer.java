package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 转池成本结转单（头）：一次台账换池一张，冻结转出数量/生物量与分配口径。
 */
@TableName("t_cost_transfer")
public class CostTransfer extends BaseEntity {

    public static final String BASIS_QBI = "QBI";

    private String transferNo;
    private Long transferRecordId;
    private Long fromPondId;
    private Long toPondId;
    private Long sourceBatchId;
    private Integer movedQuantity;
    private BigDecimal movedBiomassKg;
    private String basis;
    private BigDecimal totalAmount;
    private String allocationJson;
    private String requestId;

    public String getTransferNo() { return transferNo; }
    public void setTransferNo(String transferNo) { this.transferNo = transferNo; }
    public Long getTransferRecordId() { return transferRecordId; }
    public void setTransferRecordId(Long transferRecordId) { this.transferRecordId = transferRecordId; }
    public Long getFromPondId() { return fromPondId; }
    public void setFromPondId(Long fromPondId) { this.fromPondId = fromPondId; }
    public Long getToPondId() { return toPondId; }
    public void setToPondId(Long toPondId) { this.toPondId = toPondId; }
    public Long getSourceBatchId() { return sourceBatchId; }
    public void setSourceBatchId(Long sourceBatchId) { this.sourceBatchId = sourceBatchId; }
    public Integer getMovedQuantity() { return movedQuantity; }
    public void setMovedQuantity(Integer movedQuantity) { this.movedQuantity = movedQuantity; }
    public BigDecimal getMovedBiomassKg() { return movedBiomassKg; }
    public void setMovedBiomassKg(BigDecimal movedBiomassKg) { this.movedBiomassKg = movedBiomassKg; }
    public String getBasis() { return basis; }
    public void setBasis(String basis) { this.basis = basis; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getAllocationJson() { return allocationJson; }
    public void setAllocationJson(String allocationJson) { this.allocationJson = allocationJson; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
