package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 水产监管月报统计口径版本。
 * 每次发布产生新版本号，旧版本置 SUPERSEDED 冻结保留；月报生成时固化当时 ACTIVE 版本指针，
 * 已生成快照永不回改——口径调整只能发新版本，重新生成月报也只产生新版本快照。
 */
@TableName("t_report_caliber_version")
public class ReportCaliberVersion extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private String versionNo;
    private String status;
    private String remark;
    private String publishedBy;
    private LocalDateTime publishedAt;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
}
