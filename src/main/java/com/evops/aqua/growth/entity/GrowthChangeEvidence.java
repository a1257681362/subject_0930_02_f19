package com.evops.aqua.growth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 生长测量模块变更证据：方案/系数/任务/样本/预测的所有写操作留痕，只追加，operator 处理人不丢。
 */
@TableName("t_growth_change_evidence")
public class GrowthChangeEvidence extends BaseEntity {

    public static final String ENTITY_PLAN = "PLAN";
    public static final String ENTITY_COEFFICIENT = "COEFFICIENT";
    public static final String ENTITY_TASK = "TASK";
    public static final String ENTITY_SAMPLE = "SAMPLE";
    public static final String ENTITY_FORECAST = "FORECAST";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_CANCEL = "CANCEL";
    public static final String ACTION_GENERATE = "GENERATE";
    public static final String ACTION_RECORD = "RECORD";
    public static final String ACTION_FLAG = "FLAG";
    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_CONFIRM = "CONFIRM";
    public static final String ACTION_REJECT = "REJECT";

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
