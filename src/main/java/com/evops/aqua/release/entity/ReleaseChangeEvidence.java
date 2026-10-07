package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 出池检验模块变更证据：所有状态变化留痕（对象、动作、操作者、前后 JSON 快照、幂等请求号）。
 * 只追加，不修改不删除。
 */
@TableName("t_rel_change_evidence")
public class ReleaseChangeEvidence extends BaseEntity {

    public static final String ENTITY_RULE_VERSION = "RULE_VERSION";
    public static final String ENTITY_ORDER = "ORDER";
    public static final String ENTITY_ROUND = "ROUND";
    public static final String ENTITY_SAMPLE = "SAMPLE";
    public static final String ENTITY_REPORT = "REPORT";
    public static final String ENTITY_RESULT = "RESULT";
    public static final String ENTITY_SIGNER = "SIGNER";
    public static final String ENTITY_SHIPMENT = "SHIPMENT";
    public static final String ENTITY_PLAYBACK = "PLAYBACK";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_ENTER = "ENTER";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_RETURN = "RETURN";
    public static final String ACTION_SIGN = "SIGN";
    public static final String ACTION_SHIP = "SHIP";
    public static final String ACTION_REPLAY = "REPLAY";

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
