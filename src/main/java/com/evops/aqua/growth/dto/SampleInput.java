package com.evops.aqua.growth.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 现场录入的单尾样本：原始体重（g）与原始体长（mm）。
 * 异常样本 abnormalityFlag=TRUE 时必须携带 reviewReason（复核理由）；
 * 系统只做标记，绝不删除该样本。
 */
public class SampleInput {

    @NotNull(message = "样本序号不能为空")
    private Integer seqNo;

    @NotNull(message = "原始体重不能为空")
    @DecimalMin(value = "0.001", message = "原始体重必须大于0")
    private BigDecimal rawWeightG;

    @NotNull(message = "原始体长不能为空")
    @DecimalMin(value = "0.001", message = "原始体长必须大于0")
    private BigDecimal rawLengthMm;

    private Boolean abnormalityFlag;
    private String reviewReason;

    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }
    public BigDecimal getRawWeightG() { return rawWeightG; }
    public void setRawWeightG(BigDecimal rawWeightG) { this.rawWeightG = rawWeightG; }
    public BigDecimal getRawLengthMm() { return rawLengthMm; }
    public void setRawLengthMm(BigDecimal rawLengthMm) { this.rawLengthMm = rawLengthMm; }
    public Boolean getAbnormalityFlag() { return abnormalityFlag; }
    public void setAbnormalityFlag(Boolean abnormalityFlag) { this.abnormalityFlag = abnormalityFlag; }
    public String getReviewReason() { return reviewReason; }
    public void setReviewReason(String reviewReason) { this.reviewReason = reviewReason; }
}
