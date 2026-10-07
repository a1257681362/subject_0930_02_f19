package com.evops.aqua.tide.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 潮汐模块变更证据：所有修改统一落表，只追加。
 * 每条证据必须带操作者（operator），含操作前后 JSON 快照与幂等请求号。
 */
@TableName("t_tide_change_evidence")
public class TideChangeEvidence extends BaseEntity {

    public static final String ENTITY_EVENT = "EVENT";
    public static final String ENTITY_BAN = "WEATHER_BAN";
    public static final String ENTITY_SHIFT = "CREW_SHIFT";
    public static final String ENTITY_DEVICE = "DEVICE";
    public static final String ENTITY_WINDOW = "WINDOW";
    public static final String ENTITY_SCHEDULE = "SCHEDULE";
    public static final String ENTITY_NOTIFICATION = "NOTIFICATION";

    public static final String ACTION_REGISTER_EVENT = "REGISTER_EVENT";
    public static final String ACTION_REGISTER_BAN = "REGISTER_BAN";
    public static final String ACTION_REGISTER_SHIFT = "REGISTER_SHIFT";
    public static final String ACTION_REGISTER_DEVICE = "REGISTER_DEVICE";
    public static final String ACTION_GENERATE_WINDOW = "GENERATE_WINDOW";
    public static final String ACTION_CREATE_SCHEDULE = "CREATE_SCHEDULE";
    public static final String ACTION_PUBLISH_SCHEDULE = "PUBLISH_SCHEDULE";
    public static final String ACTION_RESCHEDULE_SCHEDULE = "RESCHEDULE_SCHEDULE";
    public static final String ACTION_POSTPONE_SCHEDULE = "POSTPONE_SCHEDULE";
    public static final String ACTION_CLOSE_WINDOW = "CLOSE_WINDOW";
    public static final String ACTION_NOTIFY_CREW = "NOTIFY_CREW";
    public static final String ACTION_ACK_NOTIFICATION = "ACK_NOTIFICATION";

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
