package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 履约订单：来源于一张已完成的捕捞订单（fishing_order_id 唯一）。
 * PENDING_SYNC：拆单已落库但海水养殖管理尚未确认，禁止一切履约动作；
 * OPEN：已确认，可入库/交接/再拆/改点；COMPLETED：全部子单签收完成。
 * total_amount 由服务端按子单行重算，拆单/再拆/改点均不改变该口径。
 */
@TableName("t_fulfill_order")
public class FulfillOrder extends BaseEntity {

    public static final String STATUS_PENDING_SYNC = "PENDING_SYNC";
    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private String orderNo;
    private Long fishingOrderId;
    private String customerName;
    private BigDecimal totalAmount;
    private String amountSnapshot;
    private String status;
    private LocalDateTime syncedTime;
    private LocalDateTime completedTime;
    private String operator;
    private String requestId;
    private String remark;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getFishingOrderId() { return fishingOrderId; }
    public void setFishingOrderId(Long fishingOrderId) { this.fishingOrderId = fishingOrderId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getAmountSnapshot() { return amountSnapshot; }
    public void setAmountSnapshot(String amountSnapshot) { this.amountSnapshot = amountSnapshot; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getSyncedTime() { return syncedTime; }
    public void setSyncedTime(LocalDateTime syncedTime) { this.syncedTime = syncedTime; }
    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
