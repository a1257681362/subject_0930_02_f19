package com.evops.aqua.fulfill.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 净重差异规则版本发布请求：版本下按规格配置容忍百分比与绝对容差。
 * 新版本发布后旧版本自动停用；requestId 幂等。
 */
public class VarianceRulePublishRequest {

    @NotEmpty(message = "规则行不能为空")
    @Valid
    private List<Line> lines;

    @NotBlank(message = "发布人不能为空")
    private String publishedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public List<Line> getLines() { return lines; }
    public void setLines(List<Line> lines) { this.lines = lines; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public static class Line {

        @NotBlank(message = "规格编码不能为空")
        private String specCode;

        @NotNull(message = "容忍百分比不能为空")
        private BigDecimal tolerancePct;

        @NotNull(message = "绝对容差不能为空")
        private BigDecimal toleranceAbsKg;

        public String getSpecCode() { return specCode; }
        public void setSpecCode(String specCode) { this.specCode = specCode; }
        public BigDecimal getTolerancePct() { return tolerancePct; }
        public void setTolerancePct(BigDecimal tolerancePct) { this.tolerancePct = tolerancePct; }
        public BigDecimal getToleranceAbsKg() { return toleranceAbsKg; }
        public void setToleranceAbsKg(BigDecimal toleranceAbsKg) { this.toleranceAbsKg = toleranceAbsKg; }
    }
}
