package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 客户订单：创建时按「规格+重量区间+交付时间」拆分为捕捞预约。
 * 状态 OPEN（在途）/ COMPLETED（全部预约完成）。
 * 完成时冻结当时生效的分级规则（gradingRuleId + gradingRuleSnapshot 快照 JSON），
 * 后续发布新规则版本不影响已完成订单。
 */
@TableName("t_fishing_order")
public class FishingOrder extends BaseEntity {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_COMPLETED = "COMPLETED";

    private String orderNo;
    private String customerName;
    private String status;
    private Long gradingRuleId;
    private String gradingRuleSnapshot;
    private LocalDateTime completedTime;
    private String operator;
    private String requestId;
    private String remark;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getGradingRuleId() { return gradingRuleId; }
    public void setGradingRuleId(Long gradingRuleId) { this.gradingRuleId = gradingRuleId; }
    public String getGradingRuleSnapshot() { return gradingRuleSnapshot; }
    public void setGradingRuleSnapshot(String gradingRuleSnapshot) { this.gradingRuleSnapshot = gradingRuleSnapshot; }
    public LocalDateTime getCompletedTime() { return completedTime; }
    public void setCompletedTime(LocalDateTime completedTime) { this.completedTime = completedTime; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
