package com.evops.aqua.cost.service;

import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.event.PondDisabledEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 池塘停用事件留痕：置为无效不改写任何费用凭条与结转快照，
 * 成本报表页面/导出/后台查询实时按 t_pond.status 同口径过滤。
 */
@Component
public class CostPondEventListener {

    private final CostEvidenceService evidenceService;

    public CostPondEventListener(CostEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onPondDisabled(PondDisabledEvent event) {
        evidenceService.record(CostChangeEvidence.ENTITY_POND, event.getPondId(),
                CostChangeEvidence.ACTION_DISABLE, null, event.getOperator(),
                null, null,
                "海水养殖管理对象（池塘）置为无效：pondId=" + event.getPondId()
                        + "，成本分摊页面/导出/后台报表同口径停止统计，历史凭条与结转快照保留不删；原因="
                        + event.getReason());
    }
}
