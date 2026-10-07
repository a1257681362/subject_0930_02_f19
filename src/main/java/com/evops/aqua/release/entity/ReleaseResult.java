package com.evops.aqua.release.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 检验结果行：某检验轮次下每个指标一行，建单时冻结阈值快照。
 * 录入值后系统按闭区间 [thresholdMin, thresholdMax] 比对，写入 qualifiedFlag 与判定说明。
 */
@TableName("t_rel_result")
public class ReleaseResult extends BaseEntity {

    private Long roundId;
    private String indicatorCode;
    private String indicatorName;
    private String unit;
    private BigDecimal thresholdMin;
    private BigDecimal thresholdMax;
    private Boolean requiredFlag;
    private String valueText;
    private Boolean qualifiedFlag;
    private String judgeDetail;

    public Long getRoundId() { return roundId; }
    public void setRoundId(Long roundId) { this.roundId = roundId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getIndicatorName() { return indicatorName; }
    public void setIndicatorName(String indicatorName) { this.indicatorName = indicatorName; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public BigDecimal getThresholdMin() { return thresholdMin; }
    public void setThresholdMin(BigDecimal thresholdMin) { this.thresholdMin = thresholdMin; }
    public BigDecimal getThresholdMax() { return thresholdMax; }
    public void setThresholdMax(BigDecimal thresholdMax) { this.thresholdMax = thresholdMax; }
    public Boolean getRequiredFlag() { return requiredFlag; }
    public void setRequiredFlag(Boolean requiredFlag) { this.requiredFlag = requiredFlag; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
    public Boolean getQualifiedFlag() { return qualifiedFlag; }
    public void setQualifiedFlag(Boolean qualifiedFlag) { this.qualifiedFlag = qualifiedFlag; }
    public String getJudgeDetail() { return judgeDetail; }
    public void setJudgeDetail(String judgeDetail) { this.judgeDetail = judgeDetail; }
}
