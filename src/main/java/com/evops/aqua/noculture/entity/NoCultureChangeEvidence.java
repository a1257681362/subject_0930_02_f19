package com.evops.aqua.noculture.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 禁养模块变更证据：规则登记/撤销、每次冻结与解冻（含仍被他规冻结的未解冻）全部留痕。
 * 只追加不修改不删除，每条证据必须带操作者（operator）。
 */
@TableName("t_noculture_change_evidence")
public class NoCultureChangeEvidence extends BaseEntity {

    public static final String ENTITY_RULE = "RULE";
    public static final String ENTITY_FREEZE = "FREEZE";
    public static final String ENTITY_COMPENSATION = "COMPENSATION";
    public static final String ENTITY_MATCH = "MATCH";

    public static final String ACTION_CREATE_RULE = "CREATE_RULE";
    public static final String ACTION_REVOKE_RULE = "REVOKE_RULE";
    public static final String ACTION_FREEZE = "FREEZE";
    public static final String ACTION_UNFREEZE = "UNFREEZE";
    public static final String ACTION_SKIP_UNFREEZE = "SKIP_UNFREEZE";
    public static final String ACTION_COMPLETED_NOTICE = "COMPLETED_NOTICE";
    public static final String ACTION_FREEZE_FAILED = "FREEZE_FAILED";
    /** 空间边界判定异常：原始请求与异常必须作为原始凭证独立事务保留，不随主事务丢失。 */
    public static final String ACTION_SPATIAL_FAILED = "SPATIAL_FAILED";

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
