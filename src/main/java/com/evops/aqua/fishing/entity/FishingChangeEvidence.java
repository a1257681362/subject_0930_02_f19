package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 捕捞预约模块变更证据：规则/库位/订单/预约/分仓/差异/换车/通知的所有写操作留痕，
 * 只追加；每条证据带操作者与幂等请求号，可按对象或 requestId 追溯。
 */
@TableName("t_fishing_change_evidence")
public class FishingChangeEvidence extends BaseEntity {

    public static final String ENTITY_GRADING_RULE = "FISHING_GRADING_RULE";
    public static final String ENTITY_BIN = "FISHING_BIN";
    public static final String ENTITY_ORDER = "FISHING_ORDER";
    public static final String ENTITY_RESERVATION = "FISHING_RESERVATION";
    public static final String ENTITY_LOAD = "FISHING_LOAD";
    public static final String ENTITY_VARIANCE = "FISHING_VARIANCE";
    public static final String ENTITY_VEHICLE_CHANGE = "FISHING_VEHICLE_CHANGE";
    public static final String ENTITY_NOTIFICATION = "FISHING_NOTIFICATION";

    public static final String ACTION_RULE_PUBLISH = "RULE_PUBLISH";
    public static final String ACTION_RULE_DISABLE = "RULE_DISABLE";
    public static final String ACTION_BIN_REGISTER = "BIN_REGISTER";
    public static final String ACTION_BIN_LOAD = "BIN_LOAD";
    public static final String ACTION_BIN_INSPECT = "BIN_INSPECT";
    public static final String ACTION_BIN_RELEASE = "BIN_RELEASE";
    public static final String ACTION_ORDER_CREATE = "ORDER_CREATE";
    public static final String ACTION_ORDER_COMPLETE = "ORDER_COMPLETE";
    public static final String ACTION_RESV_CREATE = "RESV_CREATE";
    public static final String ACTION_RESV_LOCK = "RESV_LOCK";
    public static final String ACTION_RESV_COMPLETE = "RESV_COMPLETE";
    public static final String ACTION_LOAD_RECORD = "LOAD_RECORD";
    public static final String ACTION_VARIANCE_CREATE = "VARIANCE_CREATE";
    public static final String ACTION_VEHICLE_CHANGE = "VEHICLE_CHANGE";
    public static final String ACTION_NOTIFY_SEND = "NOTIFY_SEND";
    public static final String ACTION_NOTIFY_ACK = "NOTIFY_ACK";

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
