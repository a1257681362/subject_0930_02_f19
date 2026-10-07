package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.WorkOrder;
import com.evops.aqua.maint.mapper.WorkOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 逾期自动升级：定时扫描过了完工期限（due_at）但尚未完工的工单，逐档提升优先级并留证。
 * NORMAL → WARNING（逾期即刻）→ ESCALATED（逾期超过宽限小时仍未完工）。
 * 升级只追加不回落；同一档重复扫描幂等不多写；操作者固定为调度系统标识，不丢操作者。
 */
@Service
public class OrderEscalationService {

    public static final String SCHEDULER_OPERATOR = OrderGenerationService.SCHEDULER_OPERATOR;

    /** 逾期多少小时后由 WARNING 升 ESCALATED。 */
    static final long ESCALATE_GRACE_HOURS = 24L;

    private final WorkOrderMapper orderMapper;
    private final MaintEvidenceService evidenceService;

    public OrderEscalationService(WorkOrderMapper orderMapper, MaintEvidenceService evidenceService) {
        this.orderMapper = orderMapper;
        this.evidenceService = evidenceService;
    }

    /** Quartz 定时入口：经代理调用，事务生效。 */
    @Transactional(rollbackFor = Exception.class)
    public void runScheduledScan() {
        scan(LocalDateTime.now());
    }

    /**
     * 扫描逾期工单并升级。返回升级明细，可手工触发核查。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> scan(LocalDateTime now) {
        List<WorkOrder> overdue = orderMapper.selectList(new QueryWrapper<WorkOrder>()
                .lt("due_at", now)
                .notIn("status",
                        WorkOrder.STATUS_COMPLETED,
                        WorkOrder.STATUS_ACCEPTED,
                        WorkOrder.STATUS_CLOSED)
                .orderByAsc("due_at"));
        List<WorkOrder> escalated = new ArrayList<>();
        for (WorkOrder order : overdue) {
            String target = order.getDueAt().plusHours(ESCALATE_GRACE_HOURS).isBefore(now)
                    ? WorkOrder.PRIORITY_ESCALATED : WorkOrder.PRIORITY_WARNING;
            if (rank(target) <= rank(order.getPriority())) {
                continue; // 已在同档或更高档：幂等跳过，升级只追加不回落
            }
            String before = order.getPriority();
            order.setPriority(target);
            order.setEscalateCount(order.getEscalateCount() == null ? 1 : order.getEscalateCount() + 1);
            order.setEscalatedAt(now);
            orderMapper.updateById(order);
            escalated.add(order);
            evidenceService.record(MaintChangeEvidence.ENTITY_WORK_ORDER, order.getId(),
                    MaintChangeEvidence.ACTION_ESCALATE, null, SCHEDULER_OPERATOR,
                    before, target, "工单[" + order.getOrderNo() + "]已逾期（完工期限" + order.getDueAt()
                            + "），优先级自动升级 " + before + " -> " + target
                            + "，累计升级次数=" + order.getEscalateCount());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanAt", now);
        result.put("overdueCount", overdue.size());
        result.put("escalatedCount", escalated.size());
        result.put("escalated", escalated);
        return result;
    }

    private int rank(String priority) {
        if (WorkOrder.PRIORITY_WARNING.equals(priority)) {
            return 1;
        }
        if (WorkOrder.PRIORITY_ESCALATED.equals(priority)) {
            return 2;
        }
        return 0;
    }
}
