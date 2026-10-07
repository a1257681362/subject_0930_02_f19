package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 饲料批号：一批饲料一行，三量账。
 * 可用量 = quantityIn - quantityLocked - quantityConsumed。
 * 批号不可删除；过期或可用库存不足时禁止再被投喂单占用。
 */
@TableName("t_feed_batch")
public class FeedBatch extends BaseEntity {

    public static final String STATUS_IN_STOCK = "IN_STOCK";
    public static final String STATUS_EXHAUSTED = "EXHAUSTED";

    private String batchNo;
    private String feedName;
    private String supplier;
    private BigDecimal quantityIn;
    private BigDecimal quantityLocked;
    private BigDecimal quantityConsumed;
    private LocalDate productionDate;
    private LocalDate expiryDate;
    private String status;
    private String remark;

    /** 可用库存 = 入库 - 锁定占用 - 已消耗。 */
    public BigDecimal availableQuantity() {
        BigDecimal locked = quantityLocked == null ? BigDecimal.ZERO : quantityLocked;
        BigDecimal consumed = quantityConsumed == null ? BigDecimal.ZERO : quantityConsumed;
        return quantityIn.subtract(locked).subtract(consumed);
    }

    public String getBatchNo() { return batchNo; }
    public void setBatchNo(String batchNo) { this.batchNo = batchNo; }
    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public BigDecimal getQuantityIn() { return quantityIn; }
    public void setQuantityIn(BigDecimal quantityIn) { this.quantityIn = quantityIn; }
    public BigDecimal getQuantityLocked() { return quantityLocked; }
    public void setQuantityLocked(BigDecimal quantityLocked) { this.quantityLocked = quantityLocked; }
    public BigDecimal getQuantityConsumed() { return quantityConsumed; }
    public void setQuantityConsumed(BigDecimal quantityConsumed) { this.quantityConsumed = quantityConsumed; }
    public LocalDate getProductionDate() { return productionDate; }
    public void setProductionDate(LocalDate productionDate) { this.productionDate = productionDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
