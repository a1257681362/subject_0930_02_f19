package com.evops.aqua.med.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * 用药领用单创建请求：关联池塘、病情（疫病观察单，可空）与药品批号明细。
 * 剂量可按行覆盖药品档案默认值；为空则取档案标准剂量。
 */
public class RequisitionCreateRequest {

    @NotNull(message = "池塘不能为空")
    private Long pondId;

    private Long observationId;

    @NotBlank(message = "病情描述不能为空")
    private String diseaseDesc;

    @NotNull(message = "计划用药日不能为空")
    private LocalDate useDate;

    @NotBlank(message = "申请人不能为空")
    private String applicant;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    @NotEmpty(message = "领用明细不能为空")
    @Valid
    private List<RequisitionItemRequest> items;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getObservationId() { return observationId; }
    public void setObservationId(Long observationId) { this.observationId = observationId; }
    public String getDiseaseDesc() { return diseaseDesc; }
    public void setDiseaseDesc(String diseaseDesc) { this.diseaseDesc = diseaseDesc; }
    public LocalDate getUseDate() { return useDate; }
    public void setUseDate(LocalDate useDate) { this.useDate = useDate; }
    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public List<RequisitionItemRequest> getItems() { return items; }
    public void setItems(List<RequisitionItemRequest> items) { this.items = items; }
}
