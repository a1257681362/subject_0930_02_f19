package com.evops.aqua.event;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 批次换池事件（台账换池事务提交后由成本模块监听）。
 * 整批转移：movedQuantity=源批在养数量，remainQuantity=0，receiveBatchId 指向源批次自身；
 * 分池：源批次在原池核减为 remainQuantity，目标池派生 receiveBatchId（receiveBatchNo）接收 movedQuantity。
 * movedBiomassKg 为转出部分生物量，remainBiomassKg 为源批留存生物量。
 */
public class BatchTransferredEvent {

    private final Long transferRecordId;
    private final Long sourceBatchId;
    private final Long receiveBatchId;
    private final String receiveBatchNo;
    private final Long fromPondId;
    private final Long toPondId;
    private final LocalDate transferDate;
    private final Integer movedQuantity;
    private final Integer remainQuantity;
    private final BigDecimal movedBiomassKg;
    private final BigDecimal remainBiomassKg;
    private final boolean split;
    private final String requestId;
    private final String operator;

    public BatchTransferredEvent(Long transferRecordId, Long sourceBatchId, Long receiveBatchId,
                                 String receiveBatchNo, Long fromPondId, Long toPondId,
                                 LocalDate transferDate,
                                 Integer movedQuantity, Integer remainQuantity,
                                 BigDecimal movedBiomassKg, BigDecimal remainBiomassKg,
                                 boolean split, String requestId, String operator) {
        this.transferRecordId = transferRecordId;
        this.sourceBatchId = sourceBatchId;
        this.receiveBatchId = receiveBatchId;
        this.receiveBatchNo = receiveBatchNo;
        this.fromPondId = fromPondId;
        this.toPondId = toPondId;
        this.transferDate = transferDate;
        this.movedQuantity = movedQuantity;
        this.remainQuantity = remainQuantity;
        this.movedBiomassKg = movedBiomassKg;
        this.remainBiomassKg = remainBiomassKg;
        this.split = split;
        this.requestId = requestId;
        this.operator = operator;
    }

    public Long getTransferRecordId() { return transferRecordId; }
    public Long getSourceBatchId() { return sourceBatchId; }
    public Long getReceiveBatchId() { return receiveBatchId; }
    public String getReceiveBatchNo() { return receiveBatchNo; }
    public Long getFromPondId() { return fromPondId; }
    public Long getToPondId() { return toPondId; }
    public LocalDate getTransferDate() { return transferDate; }
    public Integer getMovedQuantity() { return movedQuantity; }
    public Integer getRemainQuantity() { return remainQuantity; }
    public BigDecimal getMovedBiomassKg() { return movedBiomassKg; }
    public BigDecimal getRemainBiomassKg() { return remainBiomassKg; }
    public boolean isSplit() { return split; }
    public String getRequestId() { return requestId; }
    public String getOperator() { return operator; }
}
