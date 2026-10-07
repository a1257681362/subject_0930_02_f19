package com.evops.aqua.sensor.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 归并判定规则版本：盐度/溶氧/温度三组正常范围。
 * 同一时刻仅一个 ACTIVE 版本；规则调整只能发布新版本，
 * 已归并读数与日报冻结归并时版本结论，新版本绝不回改旧结论。
 */
@TableName("t_sensor_rule_version")
public class SensorRuleVersion extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUPERSEDED = "SUPERSEDED";

    private String versionNo;
    private String status;
    private BigDecimal salinityMin;
    private BigDecimal salinityMax;
    private BigDecimal doMin;
    private BigDecimal doMax;
    private BigDecimal tempMin;
    private BigDecimal tempMax;
    private String remark;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getSalinityMin() { return salinityMin; }
    public void setSalinityMin(BigDecimal salinityMin) { this.salinityMin = salinityMin; }
    public BigDecimal getSalinityMax() { return salinityMax; }
    public void setSalinityMax(BigDecimal salinityMax) { this.salinityMax = salinityMax; }
    public BigDecimal getDoMin() { return doMin; }
    public void setDoMin(BigDecimal doMin) { this.doMin = doMin; }
    public BigDecimal getDoMax() { return doMax; }
    public void setDoMax(BigDecimal doMax) { this.doMax = doMax; }
    public BigDecimal getTempMin() { return tempMin; }
    public void setTempMin(BigDecimal tempMin) { this.tempMin = tempMin; }
    public BigDecimal getTempMax() { return tempMax; }
    public void setTempMax(BigDecimal tempMax) { this.tempMax = tempMax; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
