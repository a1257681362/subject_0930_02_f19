package com.evops.aqua.med.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 用药模块变更证据：所有写操作留痕（含操作者与幂等请求号），只追加。
 * 审核失败（如库存不足整体回滚）同样留证——失败原因本身就是需要保留的事实。
 */
@TableName("t_med_change_evidence")
public class MedChangeEvidence extends BaseEntity {

    public static final String ENTITY_DRUG = "MED_DRUG";
    public static final String ENTITY_BATCH = "MED_BATCH";
    public static final String ENTITY_REQUISITION = "MED_REQUISITION";
    public static final String ENTITY_WITHDRAWAL_LOCK = "MED_WITHDRAWAL_LOCK";

    public static final String ACTION_DRUG_CREATE = "DRUG_CREATE";
    public static final String ACTION_BATCH_IN = "BATCH_IN";
    public static final String ACTION_REQ_CREATE = "REQ_CREATE";
    public static final String ACTION_REQ_SUBMIT = "REQ_SUBMIT";
    public static final String ACTION_REQ_APPROVE = "REQ_APPROVE";
    public static final String ACTION_REQ_APPROVE_FAILED = "REQ_APPROVE_FAILED";
    public static final String ACTION_REQ_REJECT = "REQ_REJECT";
    public static final String ACTION_REQ_WITHDRAW = "REQ_WITHDRAW";
    public static final String ACTION_REQ_RESUBMIT = "REQ_RESUBMIT";
    public static final String ACTION_STOCK_DEDUCT = "STOCK_DEDUCT";
    public static final String ACTION_LOCK_UPDATE = "LOCK_UPDATE";
    public static final String ACTION_LOCK_RELEASE = "LOCK_RELEASE";

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
