package com.evops.aqua.fishing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 分级规则版本：按版本保存规格与重量区间（半开区间 [min, max)）。
 * 新版本发布后旧版本自动停用；现场判级永远取当时 ACTIVE 版本。
 */
@TableName("t_fishing_grading_rule")
public class FishingGradingRule extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String ruleNo;
    private Integer versionNo;
    private String status;
    private String publishedBy;
    private LocalDateTime publishedTime;
    private String requestId;
    private String remark;

    public String getRuleNo() { return ruleNo; }
    public void setRuleNo(String ruleNo) { this.ruleNo = ruleNo; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedTime() { return publishedTime; }
    public void setPublishedTime(LocalDateTime publishedTime) { this.publishedTime = publishedTime; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
