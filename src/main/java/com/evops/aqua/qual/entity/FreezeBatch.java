package com.evops.aqua.qual.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 批量冻结批次：requestId 幂等——重放返回首次批次与影响清单，不重复冻结。
 */
@TableName("t_qual_freeze_batch")
public class FreezeBatch extends BaseEntity {

    private String requestId;
    private String operator;
    private String reason;
    private Integer personCount;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getPersonCount() { return personCount; }
    public void setPersonCount(Integer personCount) { this.personCount = personCount; }
}
