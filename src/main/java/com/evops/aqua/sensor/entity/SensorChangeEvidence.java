package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 传感器接入模块变更证据：设备登记/更换、规则版本发布与停用、
 * 上传归并、复核、日报审核、隔离均留痕，只追加。
 */
@TableName("t_sensor_change_evidence")
public class SensorChangeEvidence extends BaseEntity {

    public static final String ENTITY_DEVICE = "SENSOR_DEVICE";
    public static final String ENTITY_SUCCESSION = "SENSOR_SUCCESSION";
    public static final String ENTITY_RULE_VERSION = "SENSOR_RULE_VERSION";
    public static final String ENTITY_BATCH = "SENSOR_UPLOAD_BATCH";
    public static final String ENTITY_READING = "SENSOR_READING";
    public static final String ENTITY_REVIEW_TASK = "SENSOR_REVIEW_TASK";
    public static final String ENTITY_DAILY_REPORT = "SENSOR_DAILY_REPORT";
    public static final String ENTITY_QUARANTINE = "SENSOR_QUARANTINE";

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_REPLACE = "REPLACE";
    public static final String ACTION_PUBLISH = "PUBLISH";
    public static final String ACTION_SUPERSEDE = "SUPERSEDE";
    public static final String ACTION_MERGE = "MERGE";
    public static final String ACTION_QUARANTINE = "QUARANTINE";
    public static final String ACTION_LATE_REJECT = "LATE_REJECT";
    public static final String ACTION_CONFIRM = "CONFIRM";
    public static final String ACTION_EXCLUDE = "EXCLUDE";
    public static final String ACTION_REVIEW = "REVIEW";
    public static final String ACTION_RECALC = "RECALC";

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
