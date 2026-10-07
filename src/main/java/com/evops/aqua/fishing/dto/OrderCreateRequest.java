package com.evops.aqua.fishing.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 客户订单创建请求：按「规格+重量区间+交付时间」拆分为捕捞预约，一行一条。
 * requestId 幂等——补传/重试返回首次订单，不会拆出第二批预约。
 */
public class OrderCreateRequest {

    @NotBlank(message = "客户名称不能为空")
    private String customerName;

    private String vehicleNo;

    @NotEmpty(message = "订单明细不能为空")
    @Valid
    private List<OrderLineRequest> lines;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getVehicleNo() { return vehicleNo; }
    public void setVehicleNo(String vehicleNo) { this.vehicleNo = vehicleNo; }
    public List<OrderLineRequest> getLines() { return lines; }
    public void setLines(List<OrderLineRequest> lines) { this.lines = lines; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
