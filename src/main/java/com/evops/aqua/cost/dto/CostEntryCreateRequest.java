package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 成本费用人工补录请求：饲料/人工/药品/能源按池按批归集。
 * 人工补录必须引用有效财务凭证号，无凭证或凭证已作废一律拒绝。
 */
public class CostEntryCreateRequest {

    @NotNull(message = "池塘不能为空")
    private Long pondId;

    @NotNull(message = "养殖批次不能为空")
    private Long batchId;

    @NotBlank(message = "费用类型不能为空（FEED/LABOR/MED/ENERGY）")
    private String costType;

    @NotNull(message = "费用金额不能为空")
    private BigDecimal amount;

    @NotNull(message = "费用发生日期不能为空")
    private LocalDate occurDate;

    @NotBlank(message = "人工补录必须引用财务凭证号")
    private String voucherNo;

    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getCostType() { return costType; }
    public void setCostType(String costType) { this.costType = costType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public LocalDate getOccurDate() { return occurDate; }
    public void setOccurDate(LocalDate occurDate) { this.occurDate = occurDate; }
    public String getVoucherNo() { return voucherNo; }
    public void setVoucherNo(String voucherNo) { this.voucherNo = voucherNo; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
