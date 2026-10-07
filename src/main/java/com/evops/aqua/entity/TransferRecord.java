package com.evops.aqua.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 换池转移记录：批次在养殖池之间移动时必须生成。
 * biomassKg 为本次转出部分的生物量，整批转移时等于批次当时生物量，
 * 是成本模块按数量与生物量分配未结成本的权重依据。
 */
@TableName("t_transfer_record")
public class TransferRecord extends BaseEntity {

    private Long batchId;
    private Long fromPondId;
    private Long toPondId;
    private LocalDate transferDate;
    private Integer quantity;
    private BigDecimal biomassKg;
    private String reason;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getFromPondId() { return fromPondId; }
    public void setFromPondId(Long fromPondId) { this.fromPondId = fromPondId; }
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
