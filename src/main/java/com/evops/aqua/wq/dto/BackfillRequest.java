package com.evops.aqua.wq.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 缺测/历史补录：补录只能追加，不能覆盖原采样值。
 * 补录已测值填 valueText；补录缺测说明填 missingReason。
 * requestId 为强制幂等号：重复请求不会产生第二条补录。
 */
public class BackfillRequest {

    @NotNull(message = "采样任务不能为空")
    private Long taskId;
    @NotBlank(message = "指标编码不能为空")
    private String indicatorCode;
    private String valueText;
    private String missingReason;
    @NotNull(message = "补录采样日期不能为空")
    private LocalDate backfillDate;
    @NotBlank(message = "补录请求号不能为空（用于幂等）")
    private String requestId;

    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
    public String getMissingReason() { return missingReason; }
    public void setMissingReason(String missingReason) { this.missingReason = missingReason; }
    public LocalDate getBackfillDate() { return backfillDate; }
    public void setBackfillDate(LocalDate backfillDate) { this.backfillDate = backfillDate; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
