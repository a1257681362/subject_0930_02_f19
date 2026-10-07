package com.evops.aqua.report.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 口径版本下逐指标统计口径行（版本 × 指标唯一）。
 * caliberJson 固化该指标取数表、时间字段、聚合方式、缺采样判定等口径，随版本不可变。
 */
@TableName("t_report_caliber_indicator")
public class ReportCaliberIndicator extends BaseEntity {

    private Long versionId;
    private String indicatorCode;
    private String indicatorName;
    private String caliberJson;

    public Long getVersionId() { return versionId; }
    public void setVersionId(Long versionId) { this.versionId = versionId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getCaliberJson() { return caliberJson; }
    public void setCaliberJson(String caliberJson) { this.caliberJson = caliberJson; }
}
