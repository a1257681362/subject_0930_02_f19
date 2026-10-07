package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成本费用凭条：饲料/人工/药品/能源四类费用按「池+批」归集，只追加。
 * source：MANUAL 人工补录（必引凭证）/ CARRYOVER 转池分配转入 / ADJUSTMENT 差异调整入账。
 */
@TableName("t_cost_entry")
public class CostEntry extends BaseEntity {

    public static final String TYPE_FEED = "FEED";
    public static final String TYPE_LABOR = "LABOR";
    public static final String TYPE_MED = "MED";
    public static final String TYPE_ENERGY = "ENERGY";

    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_CARRYOVER = "CARRYOVER";
    public static final String SOURCE_ADJUSTMENT = "ADJUSTMENT";

    private String entryNo;
    private Long pondId;
    private Long batchId;
    private String costType;
    private BigDecimal amount;
    private LocalDate occurDate;
    private String source;
    private Long voucherId;
    private String voucherNo;
    private String ledgerType;
    private Long ledgerRecordId;
    private Long transferId;
    private Long carryoverId;
    private Long adjustmentId;
    private String batchStatusAtEntry;
    private String requestId;
    private String operator;
    private String remark;

    public String getEntryNo() { return entryNo; }
    public void setEntryNo(String entryNo) { this.entryNo = entryNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getOccurDate() { return occurDate; }
    public void setOccurDate(LocalDate occurDate) { this.occurDate = occurDate; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getVoucherId() { return voucherId; }
    public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }
    public String getVoucherNo() { return voucherNo; }
    public void setVoucherNo(String voucherNo) { this.voucherNo = voucherNo; }
    public String getLedgerType() { return ledgerType; }
    public void setLedgerType(String ledgerType) { this.ledgerType = ledgerType; }
    public Long getLedgerRecordId() { return ledgerRecordId; }
    public void setLedgerRecordId(Long ledgerRecordId) { this.ledgerRecordId = ledgerRecordId; }
    public Long getTransferId() { return transferId; }
    public void setTransferId(Long transferId) { this.transferId = transferId; }
    public Long getCarryoverId() { return carryoverId; }
    public void setCarryoverId(Long carryoverId) { this.carryoverId = carryoverId; }
    public Long getAdjustmentId() { return adjustmentId; }
    public void setAdjustmentId(Long adjustmentId) { this.adjustmentId = adjustmentId; }
    public String getBatchStatusAtEntry() { return batchStatusAtEntry; }
    public void setBatchStatusAtEntry(String batchStatusAtEntry) { this.batchStatusAtEntry = batchStatusAtEntry; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
