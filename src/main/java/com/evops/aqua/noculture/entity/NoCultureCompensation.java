package com.evops.aqua.noculture.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 撤销补偿账：每条冻结至多一条补偿，与冻结账一一对应（uk freeze_id）。
 * RELEASED 已补偿解冻；
 * SKIPPED_STILL_BOUND 部分成功——批次仍被其他重叠生效规则冻结，本次不解冻（账仍对应、可勾稽）。
 * 禁养区域撤销时的补偿记录与当时的空间匹配记录彼此对应：同一 ruleId+batchId 贯穿匹配→冻结→补偿。
 */
@TableName("t_noculture_compensation")
public class NoCultureCompensation extends BaseEntity {

    public static final String RESULT_RELEASED = "RELEASED";
    public static final String RESULT_SKIPPED_STILL_BOUND = "SKIPPED_STILL_BOUND";

    private Long ruleId;
    private Long freezeId;
    private Long batchId;
    private String result;
    private String detail;
    private String operator;
    private String requestId;

    public Long getRuleId() { return ruleId; }
    public void setRuleId(Long ruleId) { this.ruleId = ruleId; }
    public Long getFreezeId() { return freezeId; }
    public void setFreezeId(Long freezeId) { this.freezeId = freezeId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
