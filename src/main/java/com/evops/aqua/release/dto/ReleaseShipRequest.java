package com.evops.aqua.release.dto;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 批次进入发货登记：必须先有一张已授权电子签名（SIGNED）的检验单。
 * requestId 强制幂等，同检验单只允许一次发货。
 */
public class ReleaseShipRequest {

    @NotNull(message = "发货日期不能为空")
    private LocalDate shipDate;

    @NotNull(message = "发货数量不能为空")
    @Min(value = 1, message = "发货数量必须大于0")
    private Integer quantity;

    private String consignee;

    @NotBlank(message = "发货操作人不能为空")
    private String operator;

    private String requestId;

    public LocalDate getShipDate() { return shipDate; }
    public void setShipDate(LocalDate shipDate) { this.shipDate = shipDate; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getConsignee() { return consignee; }
    public void setConsignee(String consignee) { this.consignee = consignee; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
