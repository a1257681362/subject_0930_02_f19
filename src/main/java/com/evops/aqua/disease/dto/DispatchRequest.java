package com.evops.aqua.disease.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 隔离调度请求：把观察单对应批次调入指定隔离池。
 * 转移前校验池容量、消毒状态、目标池调入权限；失败时汇总说明全部冲突原因。
 * 调度人必填；requestId 幂等，重复调度返回首次隔离单。
 */
public class DispatchRequest {

    @NotNull(message = "目标隔离池不能为空")
    private Long toPondId;

    /** 调度日期（用于消毒有效期判定）；为空按当天。 */
    private LocalDate dispatchDate;

    @NotBlank(message = "调度人不能为空")
    private String operator;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    private String remark;

    public Long getToPondId() { return toPondId; }
    public void setToPondId(Long toPondId) { this.toPondId = toPondId; }
    public LocalDate getDispatchDate() { return dispatchDate; }
    public void setDispatchDate(LocalDate dispatchDate) { this.dispatchDate = dispatchDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
