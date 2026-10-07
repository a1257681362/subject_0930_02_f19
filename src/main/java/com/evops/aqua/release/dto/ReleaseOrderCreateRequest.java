package com.evops.aqua.release.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建出池检验单：指定批次与用途；品种取自投苗批次，系统按「品种 + 用途」匹配生效阈值版本。
 * 建单即冻结当时规则版本并生成初检轮次（round_no=1）与初检样品（样品↔报告 1:1 预留）。
 */
public class ReleaseOrderCreateRequest {

    @NotNull(message = "投苗批次id不能为空")
    private Long batchId;

    @NotBlank(message = "用途编码不能为空")
    private String usageCode;

    @NotBlank(message = "用途名称不能为空")
    private String usageName;

    /** 样品编号可空：为空时系统按规则生成；一旦提供必须全局唯一 */
    private String sampleNo;
    private String sampler;

    private String requestId;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getUsageCode() { return usageCode; }
    public void setUsageCode(String usageCode) { this.usageCode = usageCode; }
    public String getUsageName() { return usageName; }
    public void setUsageName(String usageName) { this.usageName = usageName; }
    public String getSampleNo() { return sampleNo; }
    public void setSampleNo(String sampleNo) { this.sampleNo = sampleNo; }
    public String getSampler() { return sampler; }
    public void setSampler(String sampler) { this.sampler = sampler; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
