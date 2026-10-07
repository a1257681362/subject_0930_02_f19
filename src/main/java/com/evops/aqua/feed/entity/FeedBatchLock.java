package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 饲料批号占用明细：投喂单锁定时为每个明细对具体批号加锁，执行后转消耗、解锁则释放。
 */
@TableName("t_feed_batch_lock")
public class FeedBatchLock extends BaseEntity {

    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_RELEASED = "RELEASED";
    public static final String STATUS_CONSUMED = "CONSUMED";

    private Long feedBatchId;
    private Long orderItemId;
    private BigDecimal quantityKg;
    private String status;

    public Long getFeedBatchId() { return feedBatchId; }
    public void setFeedBatchId(Long feedBatchId) { this.feedBatchId = feedBatchId; }
    public Long getOrderItemId() { return orderItemId; }
    public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
