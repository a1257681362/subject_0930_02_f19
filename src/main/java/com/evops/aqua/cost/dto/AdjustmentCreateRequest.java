package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成本差异调整单创建请求：账面修正只能走差异单，建单即 PENDING，审核通过后才入余额。
 * 必须引用凭证、填写差异原因；已结转（已出池）批次禁止建单。
 */
public class AdjustmentCreateRequest {

    @NotNull(message = "养殖批次不能为空")
    private Long batchId;

    @NotBlank(message = "费用类型不能为空（FEED/LABOR/MED/ENERGY）")
    private String costType;

    @NotBlank(message = "调整方向不能为空（ADD/SUB）")
    private String direction;

    @NotNull(message = "差异金额不能为空")
    private BigDecimal amount;

    @NotNull(message = "差异发生日期不能为空")
    private LocalDate occurDate;

    @NotBlank(message = "差异调整必须引用财务凭证号")
    private String voucherNo;

    @NotBlank(message = "差异原因不能为空")
    private String reason;

    @NotBlank(message = "申请人不能为空")
    private String applicant;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }
    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getOccurDate() { return occurDate; }
    public void setOccurDate(LocalDate occurDate) { this.occurDate = occurDate; }
    public String getVoucherNo() { return voucherNo; }
    public void setVoucherNo(String voucherNo) { this.voucherNo = voucherNo; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
