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
    public static final String ENTITY_RISK_WATCH = "SENSOR_RISK_WATCH";

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
    /** 高风险观察：开始观察（限制离线归并与日报汇总） */
    public static final String ACTION_WATCH_ON = "WATCH_ON";
    /** 高风险观察：解除观察（恢复离线归并与日报汇总） */
    public static final String ACTION_WATCH_OFF = "WATCH_OFF";
    /** 高风险观察期间拒绝离线归并/日报汇总，独立事务留痕 */
    public static final String ACTION_DENIED = "DENIED";

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
