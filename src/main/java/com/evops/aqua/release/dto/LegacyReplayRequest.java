package com.evops.aqua.release.dto;

import javax.validation.constraints.NotBlank;

/**
 * 旧数据回放：只读回放海水养殖台账（aqua-1）数据，核对旧抽检/出池口径与新版本差异。
 * 同一 requestId 重复请求返回首次回放结果，不产生第二条记录、不修改任何旧数据。
 */
public class LegacyReplayRequest {

    @NotBlank(message = "回放请求号不能为空")
    private String requestId;

    /** 可空：只回放指定批次；为空回放全部台账批次 */
    private Long batchId;

    private String operator;

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
}
