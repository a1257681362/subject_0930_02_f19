package com.evops.aqua.tide.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * 窗口生成请求：按海区按日取高潮潮汐事件，向前后各推 marginMinutes 分钟生成候选，
 * 叠加禁作天气与人员班次后形成可执行窗口。被排除的候选逐条说明原因。
 * requestId 幂等：重复生成返回首次结果；同一潮汐事件只生成一个窗口。
 */
public class WindowGenerateRequest {

    @NotBlank(message = "海区不能为空")
    private String seaArea;

    @NotNull(message = "生成日期不能为空")
    private LocalDate date;

    /** 高潮前后各推的分钟数；为空按 60 分钟。 */
    private Integer marginMinutes;

    @NotBlank(message = "操作者不能为空")
    private String operator;

    @NotBlank(message = "请求幂等号不能为空")
    private String requestId;

    public String getSeaArea() { return seaArea; }
    public void setSeaArea(String seaArea) { this.seaArea = seaArea; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public Integer getMarginMinutes() { return marginMinutes; }
    public void setMarginMinutes(Integer marginMinutes) { this.marginMinutes = marginMinutes; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
