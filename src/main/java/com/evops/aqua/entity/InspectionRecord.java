package com.evops.aqua.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 抽检记录：按池按批抽样检查长势与成活率。
 */
@TableName("t_inspection_record")
public class InspectionRecord extends BaseEntity {

    private Long pondId;
    private Long batchId;
    private LocalDate inspectDate;
    private Integer sampleCount;
    private BigDecimal avgWeightG;
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
