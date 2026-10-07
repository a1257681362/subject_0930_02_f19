package com.evops.aqua.cost.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 财务凭证登记请求：人工补录成本必须先登记凭证，再按凭证号引用。
 */
public class VoucherCreateRequest {

    @NotBlank(message = "凭证号不能为空")
    private String voucherNo;

    @NotBlank(message = "凭证类型不能为空")
    private String voucherType;

    @NotNull(message = "凭证日期不能为空")
    private LocalDate voucherDate;

    @NotNull(message = "凭证金额不能为空")
    private BigDecimal amount;

    private String summary;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public String getVoucherNo() { return voucherNo; }
    public void setVoucherNo(String voucherNo) { this.voucherNo = voucherNo; }
    public String getVoucherType() { return voucherType; }
    public void setVoucherType(String voucherType) { this.voucherType = voucherType; }
    public LocalDate getVoucherDate() { return voucherDate; }
    public void setVoucherDate(LocalDate voucherDate) { this.voucherDate = voucherDate; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
