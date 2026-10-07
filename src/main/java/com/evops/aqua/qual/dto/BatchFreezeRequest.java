package com.evops.aqua.qual.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量冻结请求：requestId 幂等——重放返回首次批次与影响清单，不重复冻结。
 */
public class BatchFreezeRequest {

    @NotEmpty(message = "冻结人员名单不能为空")
    private List<String> personCodes;
    @NotBlank(message = "冻结原因不能为空")
    private String reason;
    @NotBlank(message = "操作者不能为空")
    private String operator;
    @NotBlank(message = "幂等请求号不能为空")
    private String requestId;

    public List<String> getPersonCodes() { return personCodes; }
    public void setPersonCodes(List<String> personCodes) { this.personCodes = personCodes; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
