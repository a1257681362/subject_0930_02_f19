package com.evops.aqua.growth.dto;

import javax.validation.Valid;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * 现场录入：测量员录入本任务抽样品的体重与长度。
 * 录入后系统按任务冻结的设备系数逐样本校正，并按"未标记异常样本"计算均值；
 * 异常样本在 samples 中置 abnormalityFlag=TRUE 且必须填 reviewReason——只标记，不删除。
 * totalCount 为当前在池（估）总尾数，主管确认时用于测算预测出池量。
 */
public class SampleRecordRequest {

    @NotBlank(message = "测量处理人不能为空")
    private String measuredBy;

    @NotNull(message = "在池总尾数不能为空")
    @Min(value = 1, message = "在池总尾数必须大于0")
    private Integer totalCount;

    @NotEmpty(message = "至少录入一尾样本")
    @Valid
    private List<SampleInput> samples;

    private String requestId;
    private String remark;

    public String getMeasuredBy() { return measuredBy; }
    public void setMeasuredBy(String measuredBy) { this.measuredBy = measuredBy; }
    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }
    public List<SampleInput> getSamples() { return samples; }
    public void setSamples(List<SampleInput> samples) { this.samples = samples; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
