package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 渔获订单履约模块变更证据：主数据/拆单/再拆/改点审批/入库/交接冻结/签收/外部同步成败的所有写操作留痕，
 * 只追加；每条证据带操作者与幂等请求号，可按对象或 requestId 追溯。
 */
@TableName("t_fulfill_change_evidence")
public class FulfillChangeEvidence extends BaseEntity {

    public static final String ENTITY_DELIVERY_POINT = "DELIVERY_POINT";
    public static final String ENTITY_COLD_STORE = "COLD_STORE";
    public static final String ENTITY_VARIANCE_RULE = "VARIANCE_RULE";
    public static final String ENTITY_FULFILL_ORDER = "FULFILL_ORDER";
    public static final String ENTITY_FULFILL_DELIVERY = "FULFILL_DELIVERY";
    public static final String ENTITY_POINT_CHANGE = "POINT_CHANGE";
    public static final String ENTITY_FULFILL_BOX = "FULFILL_BOX";
    public static final String ENTITY_FULFILL_HANDOVER = "FULFILL_HANDOVER";
    public static final String ENTITY_FULFILL_OUTBOX = "FULFILL_OUTBOX";

    public static final String ACTION_POINT_REGISTER = "POINT_REGISTER";
    public static final String ACTION_STORE_REGISTER = "STORE_REGISTER";
    public static final String ACTION_RULE_PUBLISH = "RULE_PUBLISH";
    public static final String ACTION_RULE_DISABLE = "RULE_DISABLE";
    public static final String ACTION_ORDER_CREATE = "ORDER_CREATE";
    public static final String ACTION_ORDER_COMPLETE = "ORDER_COMPLETE";
    public static final String ACTION_DELIVERY_CREATE = "DELIVERY_CREATE";
    public static final String ACTION_DELIVERY_SPLIT = "DELIVERY_SPLIT";
    public static final String ACTION_POINT_CHANGE_SUBMIT = "POINT_CHANGE_SUBMIT";
    public static final String ACTION_POINT_CHANGE_APPROVE = "POINT_CHANGE_APPROVE";
    public static final String ACTION_POINT_CHANGE_REJECT = "POINT_CHANGE_REJECT";
    public static final String ACTION_DELIVERY_POINT_REPLACE = "DELIVERY_POINT_REPLACE";
    public static final String ACTION_BOX_INTAKE = "BOX_INTAKE";
    public static final String ACTION_BOX_HANDOVER = "BOX_HANDOVER";
    public static final String ACTION_BOX_FREEZE = "BOX_FREEZE";
    public static final String ACTION_HANDOVER_CREATE = "HANDOVER_CREATE";
    public static final String ACTION_HANDOVER_SIGN = "HANDOVER_SIGN";
    public static final String ACTION_SYNC_OK = "SYNC_OK";
    public static final String ACTION_SYNC_FAIL = "SYNC_FAIL";

    public static final String OPERATOR_GATEWAY = "system-gateway";
    public static final String OPERATOR_SCHEDULER = "system-scheduler";

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
