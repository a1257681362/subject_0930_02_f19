package com.evops.aqua.report.dto;

/**
 * 单个指标的口径覆盖项。caliber 为该指标统计口径的自由结构说明
 * （取数表、时间字段、聚合方式、缺采样判定等），发布时随版本固化为 JSON。
 */
public class CaliberIndicatorInput {

    private String indicatorCode;
    private Object caliber;

    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public Object getCaliber() { return caliber; }
    public void setCaliber(Object caliber) { this.caliber = caliber; }
}
