package com.evops.aqua.qual.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 岗位变更历史：只追加，永不删除。历史操作（证书、临时授权、证据）全部保留可追溯。
 */
@TableName("t_qual_role_change")
public class RoleChange extends BaseEntity {

    private Long personId;
    private String fromRole;
    private String toRole;
    private String reason;
    private String operator;
    private LocalDateTime changedAt;
    private String requestId;

    public Long getPersonId() { return personId; }
    public void setPersonId(Long personId) { this.personId = personId; }
    public String getFromRole() { return fromRole; }
    public void setFromRole(String fromRole) { this.fromRole = fromRole; }
    public String getToRole() { return toRole; }
    public void setToRole(String toRole) { this.toRole = toRole; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
