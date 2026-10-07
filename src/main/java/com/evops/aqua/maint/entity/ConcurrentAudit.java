package com.evops.aqua.maint.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 并发操作裁决审计：海水养殖管理同一操作并发发起时，按业务键串行裁决并逐请求留痕。
 * verdict：EFFECTIVE 生效（先拿到业务键执行权者）/ CONCURRENT_REJECTED 被拒绝（并发失败者）。
 * 被拒行记录生效请求号与生效操作者、拒绝原因——谁生效、谁被拒绝可双向追溯。只追加。
 */
@TableName("t_maint_concurrent_audit")
public class ConcurrentAudit extends BaseEntity {

    public static final String VERDICT_EFFECTIVE = "EFFECTIVE";
    public static final String VERDICT_REJECTED = "CONCURRENT_REJECTED";

    private String bizKey;
    private String action;
    private String verdict;
    private String requestId;
    private String operator;
    private String effectiveRequestId;
    private String effectiveOperator;
    private String rejectReason;

    public String getBizKey() { return bizKey; }
    public void setBizKey(String bizKey) { this.bizKey = bizKey; }
    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }
    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getEffectiveRequestId() { return effectiveRequestId; }
    public void setEffectiveRequestId(String effectiveRequestId) { this.effectiveRequestId = effectiveRequestId; }
    public String getEffectiveOperator() { return effectiveOperator; }
    public void setEffectiveOperator(String effectiveOperator) { this.effectiveOperator = effectiveOperator; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
