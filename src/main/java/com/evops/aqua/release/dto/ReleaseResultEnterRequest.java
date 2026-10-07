package com.evops.aqua.release.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 实验室录入检验结果（暂存草稿或补齐）：写入当前检验轮次，非数值直接拒绝。
 * 录入只改当前轮次结果行，绝不触碰历史轮次（初检）数据。
 */
public class ReleaseResultEnterRequest {

    @NotEmpty(message = "至少录入一个指标结果")
    @Valid
    private List<ResultInput> items;

    private String requestId;

    public List<ResultInput> getItems() { return items; }
    public void setItems(List<ResultInput> items) { this.items = items; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public static class ResultInput {
        @javax.validation.constraints.NotBlank(message = "指标编码不能为空")
        private String indicatorCode;
        @javax.validation.constraints.NotBlank(message = "检验值不能为空")
        private String valueText;

        public String getIndicatorCode() { return indicatorCode; }
        public void setIndicatorCode(String indicatorCode) { this.indicatorCode = indicatorCode; }
        public String getValueText() { return valueText; }
        public void setValueText(String valueText) { this.valueText = valueText; }
    }
}
