package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;

/**
 * 通知签收请求：班组签收人必填，回执备注必填——签收即留回执。
 * 重复签收幂等返回同一回执，不覆盖首次签收信息。
 */
public class AckRequest {

    @NotBlank(message = "签收人不能为空")
    private String operator;

    @NotBlank(message = "回执备注不能为空")
    private String ackRemark;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getAckRemark() { return ackRemark; }
    public void setAckRemark(String ackRemark) { this.ackRemark = ackRemark; }
}
