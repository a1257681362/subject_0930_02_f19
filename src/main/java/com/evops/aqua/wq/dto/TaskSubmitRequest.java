package com.evops.aqua.wq.dto;

import javax.validation.Valid;
import java.util.List;

/**
 * 采样员提交采样任务。items 可带上最终一次录入（先暂存再提交）；
 * 提交校验：每个指标要么有值，要么勾选缺测并填写原因，否则拒绝。
 * 幂等：任务已提交/已审核时重复提交请求直接返回原任务，不产生第二条结果。
 */
public class TaskSubmitRequest {

    @Valid
    private List<TaskItemInput> items;
    /** 幂等请求号（可选）：相同请求号重复提交返回同一结果 */
    private String requestId;

    public List<TaskItemInput> getItems() { return items; }
    public void setItems(List<TaskItemInput> items) { this.items = items; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
}
