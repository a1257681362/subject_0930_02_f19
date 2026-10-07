package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 保养模块变更证据：所有状态变化与计划调整留痕（对象、动作、前后 JSON 快照、幂等请求号、操作者）。
 * 只追加不修改不删除；含定时升级在内的任何写操作都必须带操作者。
 */
@TableName("t_maint_change_evidence")
public class MaintChangeEvidence extends BaseEntity {

    public static final String ENTITY_DEVICE = "DEVICE";
    public static final String ENTITY_WINDOW = "DOWNTIME_WINDOW";
    public static final String ENTITY_PLAN = "PLAN";
    public static final String ENTITY_WORK_ORDER = "WORK_ORDER";
    public static final String ENTITY_REPLACEMENT = "REPLACEMENT";
    public static final String ENTITY_SAFETY = "SAFETY_CHECK";

    public static final String ACTION_REGISTER_DEVICE = "REGISTER_DEVICE";
    public static final String ACTION_INSTALL_INITIAL = "INSTALL_INITIAL";
    public static final String ACTION_METER_READING = "METER_READING";
    public static final String ACTION_REGISTER_WINDOW = "REGISTER_WINDOW";
    public static final String ACTION_CREATE_PLAN = "CREATE_PLAN";
    public static final String ACTION_ADJUST_PLAN = "ADJUST_PLAN";
    public static final String ACTION_GENERATE_ORDER = "GENERATE_ORDER";
    public static final String ACTION_GENERATE_SKIP = "GENERATE_SKIP";
    public static final String ACTION_CREATE_MANUAL_ORDER = "CREATE_MANUAL_ORDER";
    public static final String ACTION_ASSIGN = "ASSIGN";
    public static final String ACTION_START = "START";
    public static final String ACTION_REPLACE_PART = "REPLACE_PART";
    public static final String ACTION_COMPLETE = "COMPLETE";
    public static final String ACTION_ACCEPT = "ACCEPT";
    public static final String ACTION_SAFETY_CONFIRM = "SAFETY_CONFIRM";
    public static final String ACTION_CLOSE = "CLOSE";
    public static final String ACTION_ESCALATE = "ESCALATE";

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
