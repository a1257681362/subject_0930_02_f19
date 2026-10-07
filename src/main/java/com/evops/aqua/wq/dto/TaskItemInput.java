package com.evops.aqua.wq.dto;

import javax.validation.constraints.NotBlank;

/**
 * 采样任务单个指标的录入。
 * 有值填 valueText；缺测置 missingFlag=true 并在提交时填写 missingReason。
 */
public class TaskItemInput {

    @NotBlank(message = "指标编码不能为空")
    private String indicatorCode;
    private String valueText;
    private Boolean missingFlag;
    private String missingReason;

    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
    public Boolean getMissingFlag() { return missingFlag; }
    public void setMissingFlag(Boolean missingFlag) { this.missingFlag = missingFlag; }
    public String getMissingReason() { return missingReason; }
    public void setMissingReason(String missingReason) { this.missingReason = missingReason; }
}
