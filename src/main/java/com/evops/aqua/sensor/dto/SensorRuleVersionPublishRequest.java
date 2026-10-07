package com.evops.aqua.sensor.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 发布归并判定规则版本：盐度/溶氧/温度三组正常范围（闭区间）。
 * 规则调整只能发布新版本，旧版本自动停用；已归并结论不回改。
 */
public class SensorRuleVersionPublishRequest {

    @NotBlank(message = "规则版本号不能为空")
    private String versionNo;
    @NotNull(message = "盐度下限不能为空")
    private BigDecimal salinityMin;
    @NotNull(message = "盐度上限不能为空")
    private BigDecimal salinityMax;
    @NotNull(message = "溶氧下限不能为空")
    private BigDecimal doMin;
    @NotNull(message = "溶氧上限不能为空")
    private BigDecimal doMax;
    @NotNull(message = "温度下限不能为空")
    private BigDecimal tempMin;
    @NotNull(message = "温度上限不能为空")
    private BigDecimal tempMax;
    private String remark;

    public String getVersionNo() { return versionNo; }
    public void setVersionNo(String versionNo) { this.versionNo = versionNo; }
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
