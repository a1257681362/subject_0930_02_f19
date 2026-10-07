package com.evops.aqua.wq.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 采样员暂存草稿：可只填部分指标，缺测项此时也不强制原因。
 */
public class TaskDraftRequest {

    @NotEmpty(message = "草稿内容不能为空")
    @Valid
    private List<TaskItemInput> items;

    public List<TaskItemInput> getItems() { return items; }
    public void setItems(List<TaskItemInput> items) { this.items = items; }
}
