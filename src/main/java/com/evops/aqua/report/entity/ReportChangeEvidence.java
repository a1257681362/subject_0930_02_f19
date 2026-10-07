package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 月报模块变更证据：口径发布/停用、快照固化、任务失败与重试、缺权拒绝、海区授权、导出均留痕，只追加。
 */
@TableName("t_report_change_evidence")
public class ReportChangeEvidence extends BaseEntity {

    public static final String ENTITY_CALIBER = "CALIBER_VERSION";
    public static final String ENTITY_REPORT = "MONTHLY_REPORT";
    public static final String ENTITY_JOB = "REPORT_JOB";
    public static final String ENTITY_ACCESS = "ACCESS_GATE";
    public static final String ENTITY_EXPORT = "REPORT_EXPORT";
    public static final String ENTITY_SEA_GRANT = "SEA_GRANT";

    public static final String ACTION_PUBLISH = "CALIBER_PUBLISH";
    public static final String ACTION_SUPERSEDE = "CALIBER_SUPERSEDED";
    public static final String ACTION_FREEZE = "SNAPSHOT_FROZEN";
    public static final String ACTION_JOB_SUCCESS = "JOB_SUCCESS";
    public static final String ACTION_JOB_FAIL = "JOB_FAILED";
    public static final String ACTION_RETRY = "JOB_RETRY";
    public static final String ACTION_DENIED = "CALIBER_ACCESS_DENIED";
    public static final String ACTION_EXPORT = "REPORT_EXPORT";
    public static final String ACTION_GRANT = "SEA_GRANT_UPSERT";

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
