package com.evops.aqua.fishing.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 分级规则发布请求：一个版本一组规格重量区间（半开 [min, max)）。
 * 新版本发布后旧版本自动停用；requestId 幂等，重复发布返回首次版本。
 */
public class GradingRulePublishRequest {

    @NotEmpty(message = "规则明细不能为空")
    @Valid
    private List<GradingRuleLineRequest> lines;

    @NotBlank(message = "发布人不能为空")
    private String publishedBy;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    private String remark;

    public List<GradingRuleLineRequest> getLines() { return lines; }
    public void setLines(List<GradingRuleLineRequest> lines) { this.lines = lines; }
    public String getPublishedBy() { return publishedBy; }
    public void setPublishedBy(String publishedBy) { this.publishedBy = publishedBy; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
