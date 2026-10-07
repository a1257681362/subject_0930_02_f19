package com.evops.aqua.event;

/**
 * 池塘被置为无效（DISABLED）事件。
 * 成本模块据此留证；成本分摊页面/导出/后台查询始终按 t_pond.status 实时口径过滤，
 * 本事件只负责留痕，不改写任何历史费用凭条与结转快照。
 */
public class PondDisabledEvent {

    private final Long pondId;
    private final String reason;
    private final String operator;

    public PondDisabledEvent(Long pondId, String reason, String operator) {
        this.pondId = pondId;
        this.reason = reason;
        this.operator = operator;
    }

    public Long getPondId() { return pondId; }
    public String getReason() { return reason; }
    public String getOperator() { return operator; }
}
