package com.evops.aqua.fulfill.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 履约订单创建请求：来源于一张已完成的捕捞订单，按「规格 + 收货点」拆为履约子单，一行一条。
 * 金额口径由服务端按行重算（plannedNetKg × unitPrice），请求只携带单价与数量，不接受订单总额入参。
 * requestId 幂等——补传/重试返回首次订单，不会拆出第二批子单。
 */
public class FulfillOrderCreateRequest {

    @NotNull(message = "来源捕捞订单id不能为空")
    private Long fishingOrderId;

    @NotEmpty(message = "履约明细不能为空")
    @Valid
    private List<FulfillOrderLineRequest> lines;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public Long getFishingOrderId() { return fishingOrderId; }
    public void setFishingOrderId(Long fishingOrderId) { this.fishingOrderId = fishingOrderId; }
    public List<FulfillOrderLineRequest> getLines() { return lines; }
    public void setLines(List<FulfillOrderLineRequest> lines) { this.lines = lines; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
