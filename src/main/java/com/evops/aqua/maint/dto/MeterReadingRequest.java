package com.evops.aqua.maint.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 运行小时上报请求：只追加，requestId 幂等。累计读数不得回退。
 */
public class MeterReadingRequest {

    @NotNull(message = "累计运行小时不能为空")
    private BigDecimal readingHours;
    @NotNull(message = "上报日期不能为空")
    private LocalDate readingDate;
    @NotBlank(message = "上报人不能为空")
    private String operator;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;
    private String remark;

    public BigDecimal getReadingHours() { return readingHours; }
    public void setReadingHours(BigDecimal readingHours) { this.readingHours = readingHours; }
    public LocalDate getReadingDate() { return readingDate; }
    public void setReadingDate(LocalDate readingDate) { this.readingDate = readingDate; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
