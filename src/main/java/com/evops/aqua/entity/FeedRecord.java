package com.evops.aqua.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 饲料投喂记录：按池按批留痕。
 */
@TableName("t_feed_record")
public class FeedRecord extends BaseEntity {

    private Long pondId;
    private Long batchId;
    private LocalDate feedDate;
    private String feedName;
    private BigDecimal quantityKg;
    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getFeedDate() { return feedDate; }
    public void setFeedDate(LocalDate feedDate) { this.feedDate = feedDate; }
    public String getFeedName() { return feedName; }
    public void setFeedName(String feedName) { this.feedName = feedName; }
    public BigDecimal getQuantityKg() { return quantityKg; }
    public void setQuantityKg(BigDecimal quantityKg) { this.quantityKg = quantityKg; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
