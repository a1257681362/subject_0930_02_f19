package com.evops.aqua.fishing.dto;

import javax.validation.constraints.NotBlank;

/**
 * 库位检验请求：检验完成后库位状态与待检标识同一事务同步复位。
 */
public class BinInspectRequest {

    @NotBlank(message = "检验人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
