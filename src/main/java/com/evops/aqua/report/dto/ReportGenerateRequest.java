package com.evops.aqua.report.dto;

import javax.validation.constraints.Pattern;

/**
 * 月报生成请求。reportMonth 格式 yyyy-MM（手工触发缺省取当前月）；
 * seaArea 为空表示为全部在管海区逐海区生成（每海区一个任务、一张固化快照）。
 */
public class ReportGenerateRequest {

    @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "报告月份必须为 yyyy-MM 格式")
    private String reportMonth;
    private String seaArea;
    private String requestId;

    public String getReportMonth() { return reportMonth; }
    public void setReportMonth(String reportMonth) { this.reportMonth = reportMonth; }
    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
