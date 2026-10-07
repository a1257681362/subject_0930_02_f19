package com.evops.aqua.fulfill.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交接/签收单：一子单至多一张（uk delivery_id）。
 * 交接时冻结收货点快照与净重差异规则版本快照；逐箱判定超差箱冻结、正常箱出库；
 * HANDED → SIGNED：客户首签有效，重复签收不覆盖首次回执；签收后子单 DONE。
 */
@TableName("t_fulfill_handover")
public class FulfillHandover extends BaseEntity {

    public static final String STATUS_HANDED = "HANDED";
    public static final String STATUS_SIGNED = "SIGNED";

    private String handoverNo;
    private Long deliveryId;
    private Long orderId;
    private String pointSnapshot;
    private Long varianceRuleId;
    private String ruleSnapshot;
    private BigDecimal totalIntakeNetKg;
    private BigDecimal totalHandoverNetKg;
    private BigDecimal totalDiffKg;
    private Integer handedBoxCount;
    private Integer frozenBoxCount;
    private String frozenBoxNos;
    private String handoverBy;
    private LocalDateTime handoverTime;
    private String status;
    private String signBy;
    private LocalDateTime signTime;
    private String signRemark;
    private String requestId;

    public String getHandoverNo() { return handoverNo; }
    public void setHandoverNo(String handoverNo) { this.handoverNo = handoverNo; }
    public Long getDeliveryId() { return deliveryId; }
    public void setDeliveryId(Long deliveryId) { this.deliveryId = deliveryId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getPointSnapshot() { return pointSnapshot; }
    public void setPointSnapshot(String pointSnapshot) { this.pointSnapshot = pointSnapshot; }
    public Long getVarianceRuleId() { return varianceRuleId; }
    public void setVarianceRuleId(Long varianceRuleId) { this.varianceRuleId = varianceRuleId; }
    public String getRuleSnapshot() { return ruleSnapshot; }
    public void setRuleSnapshot(String ruleSnapshot) { this.ruleSnapshot = ruleSnapshot; }
    public BigDecimal getTotalIntakeNetKg() { return totalIntakeNetKg; }
    public void setTotalIntakeNetKg(BigDecimal totalIntakeNetKg) { this.totalIntakeNetKg = totalIntakeNetKg; }
    public BigDecimal getTotalHandoverNetKg() { return totalHandoverNetKg; }
    public void setTotalHandoverNetKg(BigDecimal totalHandoverNetKg) { this.totalHandoverNetKg = totalHandoverNetKg; }
    public BigDecimal getTotalDiffKg() { return totalDiffKg; }
    public void setTotalDiffKg(BigDecimal totalDiffKg) { this.totalDiffKg = totalDiffKg; }
    public Integer getHandedBoxCount() { return handedBoxCount; }
    public void setHandedBoxCount(Integer handedBoxCount) { this.handedBoxCount = handedBoxCount; }
    public Integer getFrozenBoxCount() { return frozenBoxCount; }
    public void setFrozenBoxCount(Integer frozenBoxCount) { this.frozenBoxCount = frozenBoxCount; }
    public String getFrozenBoxNos() { return frozenBoxNos; }
    public void setFrozenBoxNos(String frozenBoxNos) { this.frozenBoxNos = frozenBoxNos; }
    public String getHandoverBy() { return handoverBy; }
    public void setHandoverBy(String handoverBy) { this.handoverBy = handoverBy; }
    public LocalDateTime getHandoverTime() { return handoverTime; }
    public void setHandoverTime(LocalDateTime handoverTime) { this.handoverTime = handoverTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSignBy() { return signBy; }
    public void setSignBy(String signBy) { this.signBy = signBy; }
    public LocalDateTime getSignTime() { return signTime; }
    public void setSignTime(LocalDateTime signTime) { this.signTime = signTime; }
    public String getSignRemark() { return signRemark; }
    public void setSignRemark(String signRemark) { this.signRemark = signRemark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
