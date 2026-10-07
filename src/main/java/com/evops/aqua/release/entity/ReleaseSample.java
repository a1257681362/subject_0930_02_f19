package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 样品：每轮检验一个样品，样品编号全局唯一；与检测报告一一对应。
 */
@TableName("t_rel_sample")
public class ReleaseSample extends BaseEntity {

    private Long orderId;
    private Long roundId;
    private String sampleNo;
    private LocalDateTime collectedTime;
    private String sampler;

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public String getSampleNo() { return sampleNo; }
    public void setSampleNo(String sampleNo) { this.sampleNo = sampleNo; }
    public LocalDateTime getCollectedTime() { return collectedTime; }
    public void setCollectedTime(LocalDateTime collectedTime) { this.collectedTime = collectedTime; }
    public String getSampler() { return sampler; }
    public void setSampler(String sampler) { this.sampler = sampler; }
}
