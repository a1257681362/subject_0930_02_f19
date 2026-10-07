package com.evops.aqua.wq.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDate;

/**
 * 缺测/历史补录：只追加新行，绝不覆盖原测量项。
 * 同一 requestId 幂等：重复补录请求不能产生第二条结果。
 */
@TableName("t_wq_backfill")
public class WqBackfill extends BaseEntity {

    public static final String ENTRY_MEASURED = "MEASURED";
    public static final String ENTRY_MISSING = "MISSING";

    private Long taskItemId;
    private Long taskId;
    private String requestId;
    private String indicatorCode;
    private String valueText;
    private String missingReason;
    private LocalDate backfillDate;
    private String entryType;

    public Long getTaskItemId() { return taskItemId; }
    public void setTaskItemId(Long taskItemId) { this.taskItemId = taskItemId; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public String getIndicatorCode() { return indicatorCode; }
    public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
    public String getValueText() { return valueText; }
    public void setValueText(String valueText) { this.valueText = valueText; }
    public String getMissingReason() { return missingReason; }
    public void setMissingReason(String missingReason) { this.missingReason = missingReason; }
    public LocalDate getBackfillDate() { return backfillDate; }
    public void setBackfillDate(LocalDate backfillDate) { this.backfillDate = backfillDate; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
}
