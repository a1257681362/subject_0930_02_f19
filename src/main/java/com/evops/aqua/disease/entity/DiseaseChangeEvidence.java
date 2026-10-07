package com.evops.aqua.disease.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 疫病模块变更证据：所有修改留痕（对象、动作、操作前后 JSON 快照、幂等请求号、操作者）。
 * 调度失败也留证并写明冲突原因。只追加不修改不删除。
 */
@TableName("t_disease_change_evidence")
public class DiseaseChangeEvidence extends BaseEntity {

    public static final String ENTITY_POND_GUARD = "POND_GUARD";
    public static final String ENTITY_OBSERVATION = "OBSERVATION";
    public static final String ENTITY_ISOLATION = "ISOLATION";
    public static final String ENTITY_FOLLOWUP_TASK = "FOLLOWUP_TASK";

    public static final String ACTION_UPSERT_GUARD = "UPSERT_GUARD";
    public static final String ACTION_CREATE_OBSERVATION = "CREATE_OBSERVATION";
    public static final String ACTION_DISPATCH = "DISPATCH";
    public static final String ACTION_DISPATCH_FAILED = "DISPATCH_FAILED";
    public static final String ACTION_RELEASE = "RELEASE";
    public static final String ACTION_FOLLOWUP_GENERATE = "FOLLOWUP_GENERATE";

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
