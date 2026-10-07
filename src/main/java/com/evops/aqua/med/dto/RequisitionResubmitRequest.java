package com.evops.aqua.med.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;

/**
 * 撤回后重提请求：另立新版本领用单，不改写已撤回的历史单。
 * useDate/diseaseDesc 为空则沿用上一版；items 为空则沿用上一版明细（药品/批号/剂量）。
 */
public class RequisitionResubmitRequest {

    private LocalDate useDate;

    private String diseaseDesc;

    @Valid
    private List<RequisitionItemRequest> items;

    private String remark;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public LocalDate getUseDate() { return useDate; }
    public void setUseDate(LocalDate useDate) { this.useDate = useDate; }
    public String getDiseaseDesc() { return diseaseDesc; }
    public void setDiseaseDesc(String diseaseDesc) { this.diseaseDesc = diseaseDesc; }
    public List<RequisitionItemRequest> getItems() { return items; }
    public void setItems(List<RequisitionItemRequest> items) { this.items = items; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
