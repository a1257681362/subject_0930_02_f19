package com.evops.aqua.dto;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 抽检请求：按池按批抽样检查。
 */
public class InspectionRecordRequest {

    @NotNull(message = "养殖池不能为空")
    private Long pondId;

    @NotNull(message = "投苗批次不能为空")
    private Long batchId;

    @NotNull(message = "抽检日期不能为空")
    private LocalDate inspectDate;

    @NotNull(message = "抽样数量不能为空")
    @Min(value = 1, message = "抽样数量必须大于0")
    private Integer sampleCount;

    private BigDecimal avgWeightG;

    @DecimalMin(value = "0", message = "成活率不能为负")
    @DecimalMax(value = "100", message = "成活率不能超过100")
    private BigDecimal survivalRate;

    private String result;
    private String remark;

    public Long getPondId() { return pondId; }
    public void setPondId(Long pondId) { this.pondId = pondId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public LocalDate getInspectDate() { return inspectDate; }
    public void setInspectDate(LocalDate inspectDate) { this.inspectDate = inspectDate; }
    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }
    public BigDecimal getAvgWeightG() { return avgWeightG; }
    public void setAvgWeightG(BigDecimal avgWeightG) { this.avgWeightG = avgWeightG; }
    public BigDecimal getSurvivalRate() { return survivalRate; }
    public void setSurvivalRate(BigDecimal survivalRate) { this.survivalRate = survivalRate; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
