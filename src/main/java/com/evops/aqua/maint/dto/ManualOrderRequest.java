package com.evops.aqua.maint.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 临时工单创建请求：不依赖计划到期，手工为设备开票。
 * 必须指定停机窗口、计划起止（须落在窗口半开区间内）与至少一行备件需求；
 * 维修人员在派工环节关联。requestId 幂等——并发同 requestId 只有一个生效。
 */
public class ManualOrderRequest {

    @NotNull(message = "设备不能为空")
    private Long deviceId;
    @NotNull(message = "停机窗口不能为空")
    private Long downtimeWindowId;
    @NotNull(message = "计划开始时间不能为空")
    private LocalDateTime schedStart;
    @NotNull(message = "计划结束时间不能为空")
    private LocalDateTime schedEnd;
    @NotNull(message = "完工期限不能为空")
    private LocalDateTime dueAt;
    @NotEmpty(message = "保养工单至少关联一行备件需求")
    @Valid
    private List<PartLine> parts;
    @NotBlank(message = "保养内容不能为空")
    private String title;
    @NotBlank(message = "开票人不能为空")
    private String operator;
    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }
    public Long getDowntimeWindowId() { return downtimeWindowId; }
    public void setDowntimeWindowId(Long downtimeWindowId) { this.downtimeWindowId = downtimeWindowId; }
    public LocalDateTime getSchedStart() { return schedStart; }
    public void setSchedStart(LocalDateTime schedStart) { this.schedStart = schedStart; }
    public LocalDateTime getSchedEnd() { return schedEnd; }
    public void setSchedEnd(LocalDateTime schedEnd) { this.schedEnd = schedEnd; }
    public LocalDateTime getDueAt() { return dueAt; }
    public void setDueAt(LocalDateTime dueAt) { this.dueAt = dueAt; }
    public List<PartLine> getParts() { return parts; }
    public void setParts(List<PartLine> parts) { this.parts = parts; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    /** 备件需求行：品类 + 数量。 */
    public static class PartLine {
        @NotNull(message = "备件品类不能为空")
        private Long catalogId;
        @NotNull(message = "备件数量不能为空")
        private Integer quantity;

        public Long getCatalogId() { return catalogId; }
        public void setCatalogId(Long catalogId) { this.catalogId = catalogId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
