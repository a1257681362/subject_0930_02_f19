package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 原池与隔离池之间的数量变化：逐笔记录，只追加。
 * 调入隔离池一笔（ISOLATE_IN）、解除回原池一笔（RELEASE_BACK），操作者随行不丢。
 */
@TableName("t_disease_stock_movement")
public class DiseaseStockMovement extends BaseEntity {

    public static final String TYPE_ISOLATE_IN = "ISOLATE_IN";
    public static final String TYPE_RELEASE_BACK = "RELEASE_BACK";

    private Long isolationId;
    private Long batchId;
    private String moveType;
    private Long fromPondId;
    private Long toPondId;
    private Integer quantity;
    private String operator;
    private String requestId;
    private String remark;

    public Long getIsolationId() { return isolationId; }
    public void setIsolationId(Long isolationId) { this.isolationId = isolationId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getMoveType() { return moveType; }
    public void setMoveType(String moveType) { this.moveType = moveType; }
    public Long getFromPondId() { return fromPondId; }
    public void setFromPondId(Long fromPondId) { this.fromPondId = fromPondId; }
    public Long getToPondId() { return toPondId; }
    public void setToPondId(Long toPondId) { this.toPondId = toPondId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
