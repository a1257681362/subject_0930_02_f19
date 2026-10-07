package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 投喂单明细：建立时冻结饲料名与计划投喂量；实际投喂量、批号在执行时落定。
 * 临时减量（actual &lt; planned）必须登记减量原因与审批人。
 */
@TableName("t_feed_order_item")
public class FeedOrderItem extends BaseEntity {

    private Long orderId;
    private String feedName;
    private BigDecimal plannedQtyKg;
    private BigDecimal actualQtyKg;
    private Long feedBatchId;
    private Boolean reductionFlag;
    private String reductionReason;
    private String reductionApprovedBy;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }
    public BigDecimal getPlannedQtyKg() { return plannedQtyKg; }
    public void setPlannedQtyKg(BigDecimal plannedQtyKg) { this.plannedQtyKg = plannedQtyKg; }
    public BigDecimal getActualQtyKg() { return actualQtyKg; }
    public void setActualQtyKg(BigDecimal actualQtyKg) { this.actualQtyKg = actualQtyKg; }
    public Long getFeedBatchId() { return feedBatchId; }
    public void setFeedBatchId(Long feedBatchId) { this.feedBatchId = feedBatchId; }
    public Boolean getReductionFlag() { return reductionFlag; }
    public void setReductionFlag(Boolean reductionFlag) { this.reductionFlag = reductionFlag; }
    public String getReductionReason() { return reductionReason; }
    public void setReductionReason(String reductionReason) { this.reductionReason = reductionReason; }
    public String getReductionApprovedBy() { return reductionApprovedBy; }
    public void setReductionApprovedBy(String reductionApprovedBy) { this.reductionApprovedBy = reductionApprovedBy; }
}
