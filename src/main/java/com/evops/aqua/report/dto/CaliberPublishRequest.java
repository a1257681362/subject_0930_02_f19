package com.evops.aqua.report.dto;

import java.util.List;

/**
 * 统计口径版本发布请求：每次发布都产生新版本（不覆盖旧版本）。
 * indicators 可选，仅给出需要调整的指标口径，未给出的指标沿用系统默认口径。
 */
public class CaliberPublishRequest {

    private String remark;
    private String requestId;
    private List<CaliberIndicatorInput> indicators;

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public List<CaliberIndicatorInput> getIndicators() { return indicators; }
    public void setIndicators(List<CaliberIndicatorInput> indicators) { this.indicators = indicators; }
}
