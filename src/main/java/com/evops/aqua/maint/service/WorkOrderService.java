package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.AcceptRequest;
import com.evops.aqua.maint.dto.AssignWorkersRequest;
import com.evops.aqua.maint.dto.ManualOrderRequest;
import com.evops.aqua.maint.dto.PartReplaceRequest;
import com.evops.aqua.maint.dto.SafetyConfirmRequest;
import com.evops.aqua.maint.entity.DowntimeWindow;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintPlan;
import com.evops.aqua.maint.entity.MaintWorker;
import com.evops.aqua.maint.entity.PartReplacement;
import com.evops.aqua.maint.entity.PlanPart;
import com.evops.aqua.maint.entity.SafetyCheck;
import com.evops.aqua.maint.entity.WorkOrder;
import com.evops.aqua.maint.entity.WorkOrderPart;
import com.evops.aqua.maint.entity.WorkOrderWorker;
import com.evops.aqua.maint.mapper.MaintDeviceMapper;
import com.evops.aqua.maint.mapper.MaintPlanMapper;
import com.evops.aqua.maint.mapper.PlanPartMapper;
import com.evops.aqua.maint.mapper.SafetyCheckMapper;
import com.evops.aqua.maint.mapper.WorkOrderMapper;
import com.evops.aqua.maint.mapper.WorkOrderPartMapper;
import com.evops.aqua.maint.mapper.WorkOrderWorkerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 保养工单生命周期：
 * PLANNED → ASSIGNED（派工，关联维修人员）→ IN_PROGRESS（开工，设备停机）
 * → COMPLETED（完工，未用备件释放）→ ACCEPTED（验收通过）→ CLOSED（关闭，安全确认全部通过后设备恢复运行）。
 * 未验收不能关闭；未完成全部安全确认不能恢复运行；同一操作并发只允许一个生效（见 ConcurrencyGuard）。
 */
@Service
public class WorkOrderService {

    /** 关闭/恢复运行前必须逐项确认的通用安全检查项。 */
    public static final List<String[]> COMMON_SAFETY_ITEMS = Arrays.asList(
            new String[]{"SITE_CLEAR", "现场清理与工具清点"},
            new String[]{"GUARD_RESET", "安全防护装置复位"},
            new String[]{"SOP_CONFIRM", "操作规程与警示确认"}
    );
    /** 按设备类型追加的安全检查项。 */
    public static final Map<String, List<String[]>> TYPE_SAFETY_ITEMS = new LinkedHashMap<>();

    static {
        TYPE_SAFETY_ITEMS.put(MaintDevice.TYPE_AERATOR, Arrays.asList(
                new String[]{"INSULATION", "电气绝缘与漏电保护检查"},
                new String[]{"IMPELLER_GUARD", "叶轮防护装置复位"},
                new String[]{"NO_LOAD_RUN", "空载试运行正常"}));
        TYPE_SAFETY_ITEMS.put(MaintDevice.TYPE_PUMP_STATION, Arrays.asList(
                new String[]{"PIPE_SEAL", "管路连接密封检查"},
                new String[]{"EMERGENCY_STOP", "急停功能试验"},
                new String[]{"GROUNDING", "接地与漏电保护检查"}));
        TYPE_SAFETY_ITEMS.put(MaintDevice.TYPE_FEEDER, Arrays.asList(
                new String[]{"HOPPER_FIX", "料斗与抛撒机构固定检查"},
                new String[]{"MECH_GUARD", "转动机构防护罩复位"},
                new String[]{"POWER_RESET", "断电重启联动正常"}));
    }

    private final WorkOrderMapper orderMapper;
    private final WorkOrderPartMapper partLineMapper;
    private final WorkOrderWorkerMapper workerLineMapper;
    private final SafetyCheckMapper safetyMapper;
    private final PlanPartMapper planPartMapper;
    private final MaintPlanMapper planMapper;
    private final MaintDeviceMapper deviceMapper;
    private final MaintDeviceService deviceService;
    private final DowntimeWindowService windowService;
    private final WorkerService workerService;
    private final SpareService spareService;
    private final ConcurrencyGuard concurrencyGuard;
    private final MaintEvidenceService evidenceService;

    public WorkOrderService(WorkOrderMapper orderMapper,
                            WorkOrderPartMapper partLineMapper,
                            WorkOrderWorkerMapper workerLineMapper,
                            SafetyCheckMapper safetyMapper,
                            PlanPartMapper planPartMapper,
                            MaintPlanMapper planMapper,
                            MaintDeviceMapper deviceMapper,
                            MaintDeviceService deviceService,
                            DowntimeWindowService windowService,
                            WorkerService workerService,
                            SpareService spareService,
                            ConcurrencyGuard concurrencyGuard,
                            MaintEvidenceService evidenceService) {
        this.orderMapper = orderMapper;
        this.partLineMapper = partLineMapper;
        this.workerLineMapper = workerLineMapper;
        this.safetyMapper = safetyMapper;
        this.planPartMapper = planPartMapper;
        this.planMapper = planMapper;
        this.deviceMapper = deviceMapper;
        this.deviceService = deviceService;
        this.windowService = windowService;
        this.workerService = workerService;
        this.spareService = spareService;
        this.concurrencyGuard = concurrencyGuard;
        this.evidenceService = evidenceService;
    }

    // ------------------------------------------------------------
    // 临时工单
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public WorkOrder createManual(ManualOrderRequest request) {
        WorkOrder byRequest = orderMapper.selectOne(
                new QueryWrapper<WorkOrder>().eq("request_id", request.getRequestId()));
        if (byRequest != null) {
            return byRequest; // 幂等：重复请求返回首次工单
        }
        MaintDevice device = deviceService.getOrThrow(request.getDeviceId());
        DowntimeWindow window = windowService.getOrThrow(request.getDowntimeWindowId());
        if (!window.getDeviceId().equals(device.getId())) {
            throw new IllegalArgumentException("停机窗口[" + window.getWindowNo() + "]不属于设备["
                    + device.getDeviceCode() + "]，不能关联");
        }
        if (!DowntimeWindow.STATUS_OPEN.equals(window.getStatus())) {
            throw new IllegalArgumentException("停机窗口[" + window.getWindowNo() + "]已关闭，不能安排工单");
        }
        validateSchedRange(window, request.getSchedStart(), request.getSchedEnd());
        if (request.getDueAt() == null || request.getDueAt().isAfter(request.getSchedStart())) {
            throw new IllegalArgumentException("完工期限不能晚于计划开工时间");
        }

        // 同一窗口的并发开票按业务键裁决：先生效者占用窗口，后到并发请求被拒绝并留审计
        String bizKey = "MANUAL_ORDER_WINDOW:" + window.getId();
        ConcurrencyGuard.LeasedLock lease = concurrencyGuard.tryAcquire(
                bizKey, "CREATE_MANUAL_ORDER", request.getRequestId(), request.getOperator());
        try {
            WorkOrder order = new WorkOrder();
            order.setOrderNo(nextOrderNo(device.getId()));
            order.setPlanId(null);
            order.setDeviceId(device.getId());
            order.setTriggerSource(WorkOrder.TRIGGER_MANUAL);
            order.setDueAt(request.getDueAt());
            order.setDowntimeWindowId(window.getId());
            order.setSchedStart(request.getSchedStart());
            order.setSchedEnd(request.getSchedEnd());
            order.setPriority(WorkOrder.PRIORITY_NORMAL);
            order.setStatus(WorkOrder.STATUS_PLANNED);
            order.setPlanVersionNo(null);
            order.setDueToken(null);
            order.setFrozenDetail("临时工单：" + request.getTitle());
            order.setEscalateCount(0);
            order.setRequestId(request.getRequestId());
            orderMapper.insert(order); // uk(downtime_window_id) 兜底并发

            List<WorkOrderPart> lines = buildPartLines(order.getId(), request.getParts());
            // 建立即锁定备件：库存不足整单回滚，不残留半占用
            spareService.reserveForOrder(order, lines);

            evidenceService.record(MaintChangeEvidence.ENTITY_WORK_ORDER, order.getId(),
                    MaintChangeEvidence.ACTION_CREATE_MANUAL_ORDER, request.getRequestId(),
                    request.getOperator(), null, order,
                    "临时工单建立：设备[" + device.getDeviceCode() + "]，窗口=" + window.getWindowNo()
                            + "，内容=" + request.getTitle() + "，备件需求行=" + lines.size());
            concurrencyGuard.recordEffective(lease, "CREATE_MANUAL_ORDER");
            return order;
        } finally {
            concurrencyGuard.release(lease);
        }
    }

    // ------------------------------------------------------------
    // 计划生成（独立事务：单张工单生成失败不影响其他到期单）
    // ------------------------------------------------------------

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public WorkOrder createGenerated(Long planId, String triggerSource, String dueToken,
                                     DowntimeWindow window, LocalDateTime schedStart,
                                     LocalDateTime schedEnd, LocalDateTime dueAt,
                                     java.math.BigDecimal dueReading, String operator) {
        MaintPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw new IllegalArgumentException("保养计划不存在，id=" + planId);
        }
        MaintDevice device = deviceService.getOrThrow(plan.getDeviceId());
        if (!window.getDeviceId().equals(device.getId())
                || !DowntimeWindow.STATUS_OPEN.equals(window.getStatus())) {
            throw new IllegalArgumentException("窗口不可用于该设备：" + window.getWindowNo());
        }
        validateSchedRange(window, schedStart, schedEnd);

        WorkOrder order = new WorkOrder();
        order.setOrderNo(nextOrderNo(device.getId()));
        order.setPlanId(plan.getId());
        order.setDeviceId(device.getId());
        order.setTriggerSource(triggerSource);
        order.setDueAt(dueAt);
        order.setDowntimeWindowId(window.getId());
        order.setSchedStart(schedStart);
        order.setSchedEnd(schedEnd);
        order.setPriority(WorkOrder.PRIORITY_NORMAL);
        order.setStatus(WorkOrder.STATUS_PLANNED);
        order.setPlanVersionNo(plan.getVersionNo());
        order.setDueReading(dueReading);
        order.setDueToken(dueToken);
        order.setEscalateCount(0);
        String requestId = "GEN-" + triggerSource + "-" + plan.getId() + "-" + dueToken;
        order.setRequestId(requestId);
        String frozen = "计划" + plan.getPlanNo() + " v" + plan.getVersionNo()
                + (WorkOrder.TRIGGER_HOURS.equals(triggerSource)
                ? "，运行小时到期读数=" + dueReading + "，间隔=" + plan.getIntervalHours() + "h"
                : "，日历到期=" + dueToken.substring(1) + "，间隔=" + plan.getIntervalDays() + "天");
        order.setFrozenDetail(frozen);
        orderMapper.insert(order); // uk(plan,source,dueToken) 保证同轨同期不重复，重复重跑抛唯一冲突

        // 复制计划备件模板为工单需求行（快照），并锁定在库实物
        List<PlanPart> templates = planPartMapper.selectList(new QueryWrapper<PlanPart>()
                .eq("plan_id", planId).orderByAsc("id"));
        List<WorkOrderPart> lines = new ArrayList<>();
        for (PlanPart tpl : templates) {
            WorkOrderPart line = new WorkOrderPart();
            line.setWorkOrderId(order.getId());
            line.setCatalogId(tpl.getCatalogId());
            line.setQuantity(tpl.getQuantity());
            line.setStatus(WorkOrderPart.STATUS_PLANNED);
            partLineMapper.insert(line);
            lines.add(line);
        }
        spareService.reserveForOrder(order, lines);

        evidenceService.record(MaintChangeEvidence.ENTITY_WORK_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_GENERATE_ORDER, requestId, operator,
                null, order, "按计划生成工单（冻结" + frozen + "），窗口=" + window.getWindowNo()
                        + "，备件需求行=" + lines.size()
                        + "。已生成工单不受后续计划调整影响");
        return order;
    }

    // ------------------------------------------------------------
    // 派工 / 开工 / 换件 / 完工 / 验收 / 安全确认 / 关闭
    // ------------------------------------------------------------

    @Transactional(rollbackFor = Exception.class)
    public WorkOrder assign(Long orderId, AssignWorkersRequest request) {
        WorkOrder order = getOrThrow(orderId);
        if (!WorkOrder.STATUS_PLANNED.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有待派工(PLANNED)工单可以派工，当前状态=" + order.getStatus());
        }
        MaintDevice device = deviceService.getOrThrow(order.getDeviceId());
        List<MaintWorker> workers = workerService.requireAssignable(request.getWorkerIds(), device);

        for (MaintWorker worker : workers) {
            WorkOrderWorker line = new WorkOrderWorker();
            line.setWorkOrderId(order.getId());
            line.setWorkerId(worker.getId());
            line.setAssignedBy(request.getAssignedBy());
            line.setAssignedAt(LocalDateTime.now());
            workerLineMapper.insert(line);
        }
        order.setStatus(WorkOrder.STATUS_ASSIGNED);
        order.setLeadWorkerId(workers.get(0).getId());
        orderMapper.updateById(order);

        evidenceService.record(MaintChangeEvidence.ENTITY_WORK_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_ASSIGN, null, request.getAssignedBy(),
                null, order, "派工维修人员=" + workerNames(workers) + "，派工人=" + request.getAssignedBy());
        return order;
    }

    /**
     * 开工：设备停机（RUNNING → DOWN），生成该设备类型的安全确认清单（待确认）。
     * 同一工单的并发开工按业务键裁决：只有一个生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrder start(Long orderId, String operator, String requestId) {
        WorkOrder replay = replayByEvidence(requestId, MaintChangeEvidence.ACTION_START);
        if (replay != null) {
            return replay;
        }
        WorkOrder order = getOrThrow(orderId);
        String bizKey = "WORK_ORDER_START:" + orderId;
        ConcurrencyGuard.LeasedLock lease = concurrencyGuard.tryAcquire(
                bizKey, "START_WORK", requestId, operator);
        try {
            if (!WorkOrder.STATUS_ASSIGNED.equals(order.getStatus())) {
                throw new IllegalArgumentException("只有已派工(ASSIGNED)工单可以开工，当前状态=" + order.getStatus());
            }
            MaintDevice device = deviceService.getOrThrow(order.getDeviceId());
            if (!MaintDevice.STATUS_RUNNING.equals(device.getStatus())) {
                throw new IllegalArgumentException("设备[" + device.getDeviceCode()
                        + "]当前不是运行中，不能开工停机（状态=" + device.getStatus() + "）");
            }
            order.setStatus(WorkOrder.STATUS_IN_PROGRESS);
            orderMapper.updateById(order);
            device.setStatus(MaintDevice.STATUS_DOWN);
            deviceMapper.updateById(device);
            createSafetyChecks(order, device);

            evidenceService.record(MaintChangeEvidence.ENTITY_WORK_ORDER, order.getId(),
                    MaintChangeEvidence.ACTION_START, requestId, operator,
                    null, order, "开工保养，设备[" + device.getDeviceCode() + "]停机，操作人=" + operator);
            concurrencyGuard.recordEffective(lease, "START_WORK");
            return order;
        } finally {
            concurrencyGuard.release(lease);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public PartReplacement replacePart(Long orderId, Long catalogId, PartReplaceRequest request) {
        PartReplacement byRequest = spareService.findReplacementByRequest(request.getRequestId());
        if (byRequest != null) {
            return byRequest;
        }
        WorkOrder order = getOrThrow(orderId);
        if (!WorkOrder.STATUS_IN_PROGRESS.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有进行中(IN_PROGRESS)工单可以换件，当前状态=" + order.getStatus());
        }
        WorkOrderPart line = partLineMapper.selectOne(new QueryWrapper<WorkOrderPart>()
                .eq("work_order_id", orderId).eq("catalog_id", catalogId));
        if (line == null) {
            throw new IllegalArgumentException("工单未关联该备件品类，catalogId=" + catalogId);
        }
        MaintDevice device = deviceService.getOrThrow(order.getDeviceId());
        return spareService.replace(order, device, catalogId, request);
    }

    /** 完工：进行中 → 待验收；未使用的锁定备件释放回在库。 */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrder complete(Long orderId, String operator) {
        WorkOrder order = getOrThrow(orderId);
        if (!WorkOrder.STATUS_IN_PROGRESS.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有进行中(IN_PROGRESS)工单可以完工，当前状态=" + order.getStatus());
        }
        spareService.releaseUnused(order);
        order.setStatus(WorkOrder.STATUS_COMPLETED);
        order.setCompletedAt(LocalDateTime.now());
        orderMapper.updateById(order);
        evidenceService.record(MaintChangeEvidence.ENTITY_WORK_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_COMPLETE, null, operator,
                null, order, "维修完工待验收，完工操作人=" + operator);
        return order;
    }

    /** 验收：完工 → 验收通过。验收人与意见缺一不可；未验收不能关闭。 */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrder accept(Long orderId, AcceptRequest request) {
        WorkOrder order = getOrThrow(orderId);
        if (!WorkOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有完工待验收(COMPLETED)工单可以验收，当前状态=" + order.getStatus());
        }
        order.setStatus(WorkOrder.STATUS_ACCEPTED);
        order.setAcceptedBy(request.getAcceptedBy());
        order.setAcceptedAt(LocalDateTime.now());
        order.setAcceptRemark(request.getAcceptRemark());
        orderMapper.updateById(order);
        evidenceService.record(MaintChangeEvidence.ENTITY_WORK_ORDER, order.getId(),
                MaintChangeEvidence.ACTION_ACCEPT, null, request.getAcceptedBy(),
                null, order, "验收通过：" + request.getAcceptRemark());
        return order;
    }

    @Transactional(rollbackFor = Exception.class)
    public SafetyCheck confirmSafety(Long orderId, Long checkId, SafetyConfirmRequest request) {
        WorkOrder order = getOrThrow(orderId);
        SafetyCheck check = safetyMapper.selectById(checkId);
        if (check == null || !check.getWorkOrderId().equals(orderId)) {
            throw new IllegalArgumentException("安全确认项不存在或不属于该工单，checkId=" + checkId);
        }
        if (SafetyCheck.STATUS_CONFIRMED.equals(check.getStatus())) {
            return check; // 重复确认幂等，不覆盖首次确认人
        }
        check.setStatus(SafetyCheck.STATUS_CONFIRMED);
        check.setConfirmedBy(request.getConfirmedBy());
        check.setConfirmedAt(LocalDateTime.now());
        check.setRemark(request.getRemark());
        safetyMapper.updateById(check);
        evidenceService.record(MaintChangeEvidence.ENTITY_SAFETY, check.getId(),
                MaintChangeEvidence.ACTION_SAFETY_CONFIRM, null, request.getConfirmedBy(),
                null, check, "工单[" + order.getOrderNo() + "]安全确认项[" + check.getItemName()
                        + "]确认通过，确认人=" + request.getConfirmedBy());
        return check;
    }

    /**
     * 关闭工单 = 设备恢复运行。前置：已验收通过 + 全部安全确认项 CONFIRMED。
     * 同一设备恢复运行的并发操作按业务键裁决：一个生效，其余被拒绝并在审计中说明谁生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkOrder close(Long orderId, String operator, String requestId) {
        WorkOrder replay = replayByEvidence(requestId, MaintChangeEvidence.ACTION_CLOSE);
        if (replay != null) {
            return replay;
        }
        WorkOrder order = getOrThrow(orderId);
        String bizKey = "DEVICE_RESUME:" + order.getDeviceId();
        ConcurrencyGuard.LeasedLock lease = concurrencyGuard.tryAcquire(
                bizKey, "RESUME_DEVICE", requestId, operator);
        try {
            if (!WorkOrder.STATUS_ACCEPTED.equals(order.getStatus())) {
                throw new IllegalArgumentException("未验收工单不能关闭：工单[" + order.getOrderNo()
                        + "]当前状态=" + order.getStatus() + "，须验收通过(ACCEPTED)后才能关闭");
            }
            List<SafetyCheck> checks = safetyMapper.selectList(new QueryWrapper<SafetyCheck>()
                    .eq("work_order_id", orderId).orderByAsc("id"));
            if (checks.isEmpty()) {
                throw new IllegalArgumentException("工单缺少安全确认清单，不能恢复设备运行");
            }
            List<String> pending = new ArrayList<>();
            for (SafetyCheck check : checks) {
                if (!SafetyCheck.STATUS_CONFIRMED.equals(check.getStatus())) {
                    pending.add(check.getItemName());
                }
            }
            if (!pending.isEmpty()) {
                throw new IllegalArgumentException("设备恢复运行前必须完成全部安全确认，未确认项："
                        + String.join("、", pending));
            }

            MaintDevice device = deviceService.getOrThrow(order.getDeviceId());
            order.setStatus(WorkOrder.STATUS_CLOSED);
            order.setClosedAt(LocalDateTime.now());
            orderMapper.updateById(order);
            if (!MaintDevice.STATUS_RUNNING.equals(device.getStatus())) {
                device.setStatus(MaintDevice.STATUS_RUNNING);
                deviceMapper.updateById(device);
            }

            evidenceService.record(MaintChangeEvidence.ENTITY_WORK_ORDER, order.getId(),
                    MaintChangeEvidence.ACTION_CLOSE, requestId, operator,
                    null, order, "工单关闭，设备[" + device.getDeviceCode()
                            + "]验收通过且安全确认全部完成，恢复运行，操作人=" + operator);
            concurrencyGuard.recordEffective(lease, "RESUME_DEVICE");
            return order;
        } finally {
            concurrencyGuard.release(lease);
        }
    }

    // ------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------

    public WorkOrder getOrThrow(Long id) {
        WorkOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("保养工单不存在，id=" + id);
        }
        return order;
    }

    public List<WorkOrder> list(Long deviceId, Long planId, String status, String triggerSource) {
        QueryWrapper<WorkOrder> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.eq(planId != null, "plan_id", planId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.eq(triggerSource != null && !triggerSource.trim().isEmpty(), "trigger_source", triggerSource);
        wrapper.orderByAsc("id");
        return orderMapper.selectList(wrapper);
    }

    public List<WorkOrderWorker> listWorkers(Long orderId) {
        return workerLineMapper.selectList(new QueryWrapper<WorkOrderWorker>()
                .eq("work_order_id", orderId).orderByAsc("id"));
    }

    /** 派工视图：明细行 + 人员姓名/工号/资质，便于直接核对"工单关联了谁"。 */
    public List<Map<String, Object>> listWorkerView(Long orderId) {
        List<Map<String, Object>> view = new ArrayList<>();
        for (WorkOrderWorker line : listWorkers(orderId)) {
            MaintWorker worker = workerService.getOrThrow(line.getWorkerId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", line.getId());
            row.put("workOrderId", line.getWorkOrderId());
            row.put("workerId", worker.getId());
            row.put("workerCode", worker.getWorkerCode());
            row.put("workerName", worker.getWorkerName());
            row.put("qualifiedTypes", worker.getQualifiedTypes());
            row.put("assignedBy", line.getAssignedBy());
            row.put("assignedAt", line.getAssignedAt());
            view.add(row);
        }
        return view;
    }

    public List<WorkOrderPart> listPartLines(Long orderId) {
        return partLineMapper.selectList(new QueryWrapper<WorkOrderPart>()
                .eq("work_order_id", orderId).orderByAsc("id"));
    }

    public List<SafetyCheck> listSafetyChecks(Long orderId) {
        return safetyMapper.selectList(new QueryWrapper<SafetyCheck>()
                .eq("work_order_id", orderId).orderByAsc("id"));
    }

    // ------------------------------------------------------------
    // 内部
    // ------------------------------------------------------------

    private void validateSchedRange(DowntimeWindow window, LocalDateTime start, LocalDateTime end) {
        DowntimeWindowService.validateRange(start, end);
        if (start.isBefore(window.getWindowStart()) || end.isAfter(window.getWindowEnd())) {
            throw new IllegalArgumentException("工单计划时段 " + start + " ~ " + end
                    + " 必须落在停机窗口[" + window.getWindowNo() + "] "
                    + window.getWindowStart() + " ~ " + window.getWindowEnd() + " 内");
        }
    }

    private List<WorkOrderPart> buildPartLines(Long orderId, List<ManualOrderRequest.PartLine> requestLines) {
        List<WorkOrderPart> lines = new ArrayList<>();
        List<Long> catalogs = new ArrayList<>();
        for (ManualOrderRequest.PartLine line : requestLines) {
            if (line.getQuantity() == null || line.getQuantity() <= 0) {
                throw new IllegalArgumentException("备件需求数量必须大于0");
            }
            if (catalogs.contains(line.getCatalogId())) {
                throw new IllegalArgumentException("工单备件需求品类重复，catalogId=" + line.getCatalogId());
            }
            catalogs.add(line.getCatalogId());
            WorkOrderPart saved = new WorkOrderPart();
            saved.setWorkOrderId(orderId);
            saved.setCatalogId(line.getCatalogId());
            saved.setQuantity(line.getQuantity());
            saved.setStatus(WorkOrderPart.STATUS_PLANNED);
            partLineMapper.insert(saved);
            lines.add(saved);
        }
        return lines;
    }

    private void createSafetyChecks(WorkOrder order, MaintDevice device) {
        List<String[]> items = new ArrayList<>(COMMON_SAFETY_ITEMS);
        List<String[]> extra = TYPE_SAFETY_ITEMS.get(device.getDeviceType());
        if (extra != null) {
            items.addAll(extra);
        }
        for (String[] item : items) {
            SafetyCheck check = new SafetyCheck();
            check.setWorkOrderId(order.getId());
            check.setItemCode(item[0]);
            check.setItemName(item[1]);
            check.setStatus(SafetyCheck.STATUS_PENDING);
            safetyMapper.insert(check);
        }
    }

    private String workerNames(List<MaintWorker> workers) {
        List<String> names = new ArrayList<>();
        for (MaintWorker worker : workers) {
            names.add(worker.getWorkerName());
        }
        return String.join("、", names);
    }

    private String nextOrderNo(Long deviceId) {
        Long count = orderMapper.selectCount(
                new QueryWrapper<WorkOrder>().eq("device_id", deviceId));
        long seq = (count == null ? 0 : count) + 1;
        return "WO-" + deviceId + "-" + seq;
    }

    private WorkOrder findByRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        return orderMapper.selectOne(new QueryWrapper<WorkOrder>().eq("request_id", requestId));
    }

    /**
     * 状态动作（开工/关闭）的 requestId 只记在证据中——按动作证据反查首次工单实现幂等回放。
     */
    private WorkOrder replayByEvidence(String requestId, String action) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return null;
        }
        List<MaintChangeEvidence> evidences = evidenceService.list(
                MaintChangeEvidence.ENTITY_WORK_ORDER, null, requestId);
        for (MaintChangeEvidence evidence : evidences) {
            if (action.equals(evidence.getAction())) {
                return getOrThrow(evidence.getEntityId());
            }
        }
        return null;
    }
}
