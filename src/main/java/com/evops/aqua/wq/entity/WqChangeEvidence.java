package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 变更证据：所有修改留痕（对象、动作、操作前后 JSON 快照、幂等请求号）。
 * 只追加不修改不删除。
 */
@TableName("t_wq_change_evidence")
public class WqChangeEvidence extends BaseEntity {

    public static final String ENTITY_RULE_VERSION = "RULE_VERSION";
    public static final String ENTITY_PLAN = "PLAN";
    public static final String ENTITY_TASK = "TASK";
    public static final String ENTITY_TASK_ITEM = "TASK_ITEM";
    public static final String ENTITY_BACKFILL = "BACKFILL";
    public static final String ENTITY_HEALTH_POINT = "HEALTH_POINT";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_BACKFILL = "BACKFILL";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_GENERATE = "GENERATE";

    private String entityType;
    private Long entityId;
    private String action;
    private String requestId;
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
    public String getBeforeJson() { return beforeJson; }
    public void setBeforeJson(String beforeJson) { this.beforeJson = beforeJson; }
    public String getAfterJson() { return afterJson; }
    public void setAfterJson(String afterJson) { this.afterJson = afterJson; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
}
