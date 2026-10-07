package com.evops.aqua.fulfill.dto;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/**
 * 子单再拆请求：同收货点同规格，仅按数量切分出若干子行（不指定新收货点、不改单价）。
 * 各子行数量之和必须小于原行数量——原行保留尾差继续履约；金额按数量比例分摊、尾差归原行，
 * 全订单金额口径不变。requestId 幂等。
 */
public class DeliverySplitRequest {

    @NotEmpty(message = "拆分子行不能为空")
    @Valid
    private List<SplitChild> children;

    @NotBlank(message = "操作人不能为空")
    private String operator;

    @NotBlank(message = "请求号不能为空")
    private String requestId;

    public List<SplitChild> getChildren() { return children; }
    public void setChildren(List<SplitChild> children) { this.children = children; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public static class SplitChild {

        @NotNull(message = "子行计划净重不能为空")
        private BigDecimal plannedNetKg;

        public BigDecimal getPlannedNetKg() { return plannedNetKg; }
        public void setPlannedNetKg(BigDecimal plannedNetKg) { this.plannedNetKg = plannedNetKg; }
    }
}
