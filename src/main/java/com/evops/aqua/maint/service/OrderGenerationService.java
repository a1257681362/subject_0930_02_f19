package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.entity.DowntimeWindow;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintPlan;
import com.evops.aqua.maint.entity.WorkOrder;
import com.evops.aqua.maint.mapper.DowntimeWindowMapper;
import com.evops.aqua.maint.mapper.MaintPlanMapper;
import com.evops.aqua.maint.mapper.WorkOrderMapper;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 保养工单生成：按计划的运行小时轨与日历轨扫描到期，匹配设备开放停机窗口后生成工单。
 *
 * 口径：
 * - 小时轨：设备当前累计读数 - 上次生成基准 ≥ 间隔小时 即到期，到期读数=基准+间隔（冻结在工单）；
 * - 日历轨：上次生成基准日 + 间隔天 ≤ 今天 即到期，到期日=基准日+间隔天（冻结在工单 due_token）；
 * - 同轨存在未关闭工单时跳过（一台设备同一类保养不并行派两张单）；
 * - 到期但没有可预约的开放停机窗口：跳过并留证，不强行开票；
 * - 一张停机窗口只安排一张工单（库表 uk 兜底）；
 * - 工单成功生成后才推进计划基准；重跑幂等（uk(plan,source,due_token)）；
 * - 已经生成的工单按冻结的计划版本执行，计划调整不回改。
 */
@Service
public class OrderGenerationService {

    public static final String SCHEDULER_OPERATOR = "system-scheduler";

    private final MaintPlanMapper planMapper;
    private final WorkOrderMapper orderMapper;
    private final DowntimeWindowMapper windowMapper;
    private final MaintDeviceService deviceService;
    private final PlanService planService;
    private final WorkOrderService workOrderService;
    private final MaintEvidenceService evidenceService;

    public OrderGenerationService(MaintPlanMapper planMapper,
                                  WorkOrderMapper orderMapper,
                                  DowntimeWindowMapper windowMapper,
                                  MaintDeviceService deviceService,
                                  PlanService planService,
                                  @Lazy WorkOrderService workOrderService,
                                  MaintEvidenceService evidenceService) {
        this.planMapper = planMapper;
        this.orderMapper = orderMapper;
        this.windowMapper = windowMapper;
        this.deviceService = deviceService;
        this.planService = planService;
        this.workOrderService = workOrderService;
        this.evidenceService = evidenceService;
    }

    /**
     * 全量扫描：逐计划检查两轨到期并生成。单张工单独立事务，失败只影响该单。
     */
    public Map<String, Object> scan(LocalDateTime now) {
        List<MaintPlan> plans = planMapper.selectList(new QueryWrapper<MaintPlan>()
                .eq("status", MaintPlan.STATUS_ACTIVE).orderByAsc("id"));
        List<WorkOrder> created = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();
        for (MaintPlan plan : plans) {
            scanOne(plan, now, created, skipped);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scanAt", now);
        result.put("createdCount", created.size());
        result.put("skippedCount", skipped.size());
        result.put("created", created);
        result.put("skipped", skipped);
        return result;
    }

    private void scanOne(MaintPlan plan, LocalDateTime now,
                         List<WorkOrder> created, List<Map<String, Object>> skipped) {
        MaintDevice device = deviceService.getOrThrow(plan.getDeviceId());
        if (Integer.valueOf(1).equals(plan.getHoursEnabled())) {
            try {
                tryHoursTrack(plan, device, now, created, skipped);
            } catch (Exception ex) {
                recordTrackFailure(plan, WorkOrder.TRIGGER_HOURS, ex);
            }
        }
        if (Integer.valueOf(1).equals(plan.getCalendarEnabled())) {
            try {
                tryCalendarTrack(plan, device, now, created, skipped);
            } catch (Exception ex) {
                recordTrackFailure(plan, WorkOrder.TRIGGER_CALENDAR, ex);
            }
        }
    }

    /** 单轨生成失败（独立事务已回滚）：留失败证据，不影响其他计划继续生成。同计划同轨只留首次。 */
    private void recordTrackFailure(MaintPlan plan, String source, Exception ex) {
        String evidenceRequestId = "SCAN-FAIL-" + source + "-" + plan.getId();
        if (!evidenceService.list(MaintChangeEvidence.ENTITY_PLAN, plan.getId(), evidenceRequestId).isEmpty()) {
            return;
        }
        evidenceService.recordStandalone(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                MaintChangeEvidence.ACTION_GENERATE_SKIP, evidenceRequestId,
                SCHEDULER_OPERATOR, null, null,
                "计划[" + plan.getPlanNo() + "]" + source + "轨生成工单失败：" + ex.getMessage());
    }

    private void tryHoursTrack(MaintPlan plan, MaintDevice device, LocalDateTime now,
                               List<WorkOrder> created, List<Map<String, Object>> skipped) {
        BigDecimal base = plan.getLastGeneratedReading() == null
                ? device.getRunningHours() : plan.getLastGeneratedReading();
        BigDecimal interval = plan.getIntervalHours();
        BigDecimal current = device.getRunningHours();
        BigDecimal delta = current.subtract(base);
        if (delta.compareTo(interval) < 0) {
            return; // 未到期
        }
        BigDecimal dueReading = base.add(interval);
        String token = "H" + dueReading.stripTrailingZeros().toPlainString();
        if (hasOpenOrder(plan.getId(), WorkOrder.TRIGGER_HOURS)) {
            skip(plan, WorkOrder.TRIGGER_HOURS, token,
                    "运行小时已到期（读数" + current + "≥到期读数" + dueReading
                            + "），但同轨已有未关闭工单，不重复生成", skipped);
            return;
        }
        // 匹配尚未被工单占用、当前仍可进入的最早开放窗口（窗口结束晚于当前时间）
        DowntimeWindow window = pickWindow(device.getId(), now, null);
        if (window == null) {
            skip(plan, WorkOrder.TRIGGER_HOURS, token,
                    "运行小时已到期（到期读数" + dueReading + "），但设备没有可预约的开放停机窗口，暂不生成", skipped);
            return;
        }
        WorkOrder order = workOrderService.createGenerated(plan.getId(), WorkOrder.TRIGGER_HOURS, token,
                window, window.getWindowStart(), window.getWindowEnd(), window.getWindowEnd(),
                dueReading, SCHEDULER_OPERATOR);
        advanceHoursBaseline(plan, dueReading);
        created.add(order);
    }

    private void tryCalendarTrack(MaintPlan plan, MaintDevice device, LocalDateTime now,
                                  List<WorkOrder> created, List<Map<String, Object>> skipped) {
        LocalDate base = plan.getLastGeneratedDate() == null
                ? plan.getAnchorDate() : plan.getLastGeneratedDate();
        if (base == null) {
            return;
        }
        LocalDate dueDate = base.plusDays(plan.getIntervalDays());
        LocalDate today = now.toLocalDate();
        if (dueDate.isAfter(today)) {
            return; // 未到期
        }
        String token = "D" + dueDate.toString().replace("-", "");
        if (hasOpenOrder(plan.getId(), WorkOrder.TRIGGER_CALENDAR)) {
            skip(plan, WorkOrder.TRIGGER_CALENDAR, token,
                    "日历周期已到期（到期日" + dueDate + "），但同轨已有未关闭工单，不重复生成", skipped);
            return;
        }
        // 日历保养安排在到期日当天或之后的最早可用窗口
        DowntimeWindow window = pickWindow(device.getId(), now, dueDate.atStartOfDay());
        if (window == null) {
            skip(plan, WorkOrder.TRIGGER_CALENDAR, token,
                    "日历周期已到期（到期日" + dueDate + "），但到期日之后没有可预约的开放停机窗口，暂不生成", skipped);
            return;
        }
        WorkOrder order = workOrderService.createGenerated(plan.getId(), WorkOrder.TRIGGER_CALENDAR, token,
                window, window.getWindowStart(), window.getWindowEnd(), window.getWindowEnd(),
                null, SCHEDULER_OPERATOR);
        advanceCalendarBaseline(plan, dueDate);
        created.add(order);
    }

    /**
     * 选择设备开放、未被工单占用的最早窗口：
     * notBeforeStart 为空→窗口结束必须晚于 now（当前仍可进入）；
     * 日历轨→窗口开始不早于到期日。
     */
    private DowntimeWindow pickWindow(Long deviceId, LocalDateTime now, LocalDateTime notBeforeStart) {
        List<DowntimeWindow> windows = windowMapper.selectList(new QueryWrapper<DowntimeWindow>()
                .eq("device_id", deviceId)
                .eq("status", DowntimeWindow.STATUS_OPEN)
                .gt("window_end", now)
                .orderByAsc("window_start"));
        for (DowntimeWindow window : windows) {
            if (notBeforeStart != null && window.getWindowStart().isBefore(notBeforeStart)) {
                continue;
            }
            Long used = orderMapper.selectCount(new QueryWrapper<WorkOrder>()
                    .eq("downtime_window_id", window.getId()));
            if (used != null && used == 0L) {
                return window;
            }
        }
        return null;
    }

    private boolean hasOpenOrder(Long planId, String source) {
        Long count = orderMapper.selectCount(new QueryWrapper<WorkOrder>()
                .eq("plan_id", planId)
                .eq("trigger_source", source)
                .notIn("status", WorkOrder.STATUS_CLOSED));
        return count != null && count > 0;
    }

    /** 工单独立事务已成功提交，再以独立事务推进基准，避免"单已出、基准未进"。 */
    private void advanceHoursBaseline(MaintPlan plan, BigDecimal newReading) {
        MaintPlan latest = planService.getOrThrow(plan.getId());
        latest.setLastGeneratedReading(newReading);
        planService.updateBaselines(latest);
    }

    private void advanceCalendarBaseline(MaintPlan plan, LocalDate newDate) {
        MaintPlan latest = planService.getOrThrow(plan.getId());
        latest.setLastGeneratedDate(newDate);
        planService.updateBaselines(latest);
    }

    private void skip(MaintPlan plan, String source, String token, String reason,
                      List<Map<String, Object>> skipped) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("planId", plan.getId());
        item.put("planNo", plan.getPlanNo());
        item.put("triggerSource", source);
        item.put("dueToken", token);
        item.put("reason", reason);
        skipped.add(item);
        // 同计划同轨同到期只留首次跳过证据，周期重扫不重复刷
        String evidenceRequestId = "SCAN-" + source + "-" + plan.getId() + "-" + token;
        if (evidenceService.list(MaintChangeEvidence.ENTITY_PLAN, plan.getId(), evidenceRequestId).isEmpty()) {
            evidenceService.recordStandalone(MaintChangeEvidence.ENTITY_PLAN, plan.getId(),
                    MaintChangeEvidence.ACTION_GENERATE_SKIP, evidenceRequestId,
                    SCHEDULER_OPERATOR, null, item, reason);
        }
    }

    /** 供定时任务/手工触发按当前时刻扫描。 */
    public Map<String, Object> scanNow() {
        return scan(LocalDateTime.now());
    }
}
