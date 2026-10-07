package com.evops.aqua.cost.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 成本模块变更证据：归集/凭证/转池分配/出池结转/差异审核/池塘停用全部留痕，只追加。
 */
@TableName("t_cost_change_evidence")
public class CostChangeEvidence extends BaseEntity {

    public static final String ENTITY_VOUCHER = "COST_VOUCHER";
    public static final String ENTITY_ENTRY = "COST_ENTRY";
    public static final String ENTITY_TRANSFER = "COST_TRANSFER";
    public static final String ENTITY_CARRYOVER = "COST_CARRYOVER";
    public static final String ENTITY_ADJUSTMENT = "COST_ADJUSTMENT";
    public static final String ENTITY_POND = "POND";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_ALLOCATE = "ALLOCATE";
    public static final String ACTION_CARRYOVER = "CARRYOVER";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_DISABLE = "DISABLE";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
    private String operator;
    private String beforeJson;
    private String afterJson;
    private String detail;

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public Long getEntityId() { return entityId; }
    public void setEntityId(Long entityId) { this.entityId = entityId; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
