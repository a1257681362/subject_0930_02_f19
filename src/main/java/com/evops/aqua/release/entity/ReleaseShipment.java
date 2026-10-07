package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 发货登记：批次进入发货的唯一出口。
 * 仅检验单已由授权签字人电子签名（SIGNED）才允许登记；一单一批发货一次（uk_order_id）。
 */
@TableName("t_rel_shipment")
public class ReleaseShipment extends BaseEntity {

    private String shipmentNo;
    private Long orderId;
    private Long batchId;
    private LocalDate shipDate;
    private Integer quantity;
    private String consignee;
    private String operator;
    private String requestId;

    public String getShipmentNo() { return shipmentNo; }
    public void setShipmentNo(String shipmentNo) { this.shipmentNo = shipmentNo; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
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
