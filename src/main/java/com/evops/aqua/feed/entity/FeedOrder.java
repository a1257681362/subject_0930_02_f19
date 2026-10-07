package com.evops.aqua.feed.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 投喂单：按池按批按日。建立即冻结当时的配方版本，状态 DRAFT/LOCKED/EXECUTED。
 * feedType 区分 TRIAL（试喂）与 FORMAL（正式投喂），日汇总据此分列。
 */
@TableName("t_feed_order")
public class FeedOrder extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_EXECUTED = "EXECUTED";

    public static final String TYPE_TRIAL = "TRIAL";
    public static final String TYPE_FORMAL = "FORMAL";

    private String orderNo;
    private Long pondId;
    private Long batchId;
    private LocalDate feedDate;
    private Integer dayAge;
    private BigDecimal waterTempC;
    private String feedType;
    private Long formulaVersionId;
    private String status;
    private Long lockedBy;
    private LocalDateTime lockedTime;
    private Long executedBy;
    private LocalDateTime executedTime;
    private String remark;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getFeedDate() { return feedDate; }
    public void setFeedDate(LocalDate feedDate) { this.feedDate = feedDate; }
    public Integer getDayAge() { return dayAge; }
    public void setDayAge(Integer dayAge) { this.dayAge = dayAge; }
    public BigDecimal getWaterTempC() { return waterTempC; }
    public void setWaterTempC(BigDecimal waterTempC) { this.waterTempC = waterTempC; }
    public String getFeedType() { return feedType; }
    public void setFeedType(String feedType) { this.feedType = feedType; }
    public Long getFormulaVersionId() { return formulaVersionId; }
    public void setFormulaVersionId(Long formulaVersionId) { this.formulaVersionId = formulaVersionId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getLockedBy() { return lockedBy; }
    public void setLockedBy(Long lockedBy) { this.lockedBy = lockedBy; }
    public LocalDateTime getLockedTime() { return lockedTime; }
    public void setLockedTime(LocalDateTime lockedTime) { this.lockedTime = lockedTime; }
    public Long getExecutedBy() { return executedBy; }
    public void setExecutedBy(Long executedBy) { this.executedBy = executedBy; }
    public LocalDateTime getExecutedTime() { return executedTime; }
    public void setExecutedTime(LocalDateTime executedTime) { this.executedTime = executedTime; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
