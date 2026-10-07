package com.evops.aqua.event;

/**
 * 批次出池事件：台账出池成功、批次封存后发布。
 * 成本模块据此把未结成本全部结转并形成不可变快照。
 */
public class BatchHarvestedEvent {

    private final Long batchId;
    private final Long pondId;
    private final Long harvestRecordId;
    private final String requestId;
    private final String operator;

    public BatchHarvestedEvent(Long batchId, Long pondId, Long harvestRecordId,
                               String requestId, String operator) {
        this.batchId = batchId;
        this.pondId = pondId;
        this.harvestRecordId = harvestRecordId;
        this.requestId = requestId;
        this.operator = operator;
    }

    public Long getBatchId() { return batchId; }
    public Long getPondId() { return pondId; }
    public Long getHarvestRecordId() { return harvestRecordId; }
    public String getRequestId() { return requestId; }
    public String getOperator() { return operator; }
}
