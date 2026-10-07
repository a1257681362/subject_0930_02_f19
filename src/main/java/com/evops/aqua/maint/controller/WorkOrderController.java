package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.AcceptRequest;
import com.evops.aqua.maint.dto.AssignWorkersRequest;
import com.evops.aqua.maint.dto.ManualOrderRequest;
import com.evops.aqua.maint.dto.OrderCloseRequest;
import com.evops.aqua.maint.dto.OrderStartRequest;
import com.evops.aqua.maint.dto.PartReplaceRequest;
import com.evops.aqua.maint.dto.SafetyConfirmRequest;
import com.evops.aqua.maint.entity.PartReplacement;
import com.evops.aqua.maint.entity.SafetyCheck;
import com.evops.aqua.maint.entity.WorkOrder;
import com.evops.aqua.maint.entity.WorkOrderPart;
import com.evops.aqua.maint.entity.WorkOrderWorker;
import com.evops.aqua.maint.service.OrderEscalationService;
import com.evops.aqua.maint.service.OrderGenerationService;
import com.evops.aqua.maint.service.WorkOrderService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 保养工单全生命周期：
 * 建立（临时/计划生成）→ 派工关联维修人员 → 开工停机 → 备件替换（留旧件序列号）
 * → 完工 → 验收 → 安全确认 → 关闭恢复运行；未验收不可关闭，安全确认未完成不可恢复运行。
 */
@RestController
@RequestMapping("/api/aqua/maint/orders")
public class WorkOrderController {

    private final WorkOrderService orderService;
    private final OrderGenerationService generationService;
    private final OrderEscalationService escalationService;

    public WorkOrderController(WorkOrderService orderService,
                               OrderGenerationService generationService,
                               OrderEscalationService escalationService) {
        this.orderService = orderService;
        this.generationService = generationService;
        this.escalationService = escalationService;
    }

    /** 临时工单：关联停机窗口与备件需求，建立即锁定备件。 */
    @PostMapping("/manual")
    public ApiResponse<WorkOrder> createManual(@Validated @RequestBody ManualOrderRequest request) {
        return ApiResponse.ok(orderService.createManual(request));
    }

    /** 手工触发计划扫描：按运行小时/日历周期到期生成工单（定时任务同逻辑）。 */
    @PostMapping("/scan")
    public ApiResponse<Map<String, Object>> scanGenerate() {
        return ApiResponse.ok(generationService.scanNow());
    }

    /** 手工触发逾期升级扫描。 */
    @PostMapping("/escalate-scan")
    public ApiResponse<Map<String, Object>> scanEscalate() {
        return ApiResponse.ok(escalationService.scan(java.time.LocalDateTime.now()));
    }

    @GetMapping
    public ApiResponse<List<WorkOrder>> list(@RequestParam(required = false) Long deviceId,
                                             @RequestParam(required = false) Long planId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String triggerSource) {
        return ApiResponse.ok(orderService.list(deviceId, planId, status, triggerSource));
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkOrder> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getOrThrow(id));
    }

    @GetMapping("/{id}/workers")
    public ApiResponse<List<Map<String, Object>>> workers(@PathVariable Long id) {
        return ApiResponse.ok(orderService.listWorkerView(id));
    }

    @GetMapping("/{id}/parts")
    public ApiResponse<List<WorkOrderPart>> parts(@PathVariable Long id) {
        return ApiResponse.ok(orderService.listPartLines(id));
    }

    @GetMapping("/{id}/safety-checks")
    public ApiResponse<List<SafetyCheck>> safetyChecks(@PathVariable Long id) {
        return ApiResponse.ok(orderService.listSafetyChecks(id));
    }

    /** 派工：关联至少一名具备资质的维修人员。 */
    @PostMapping("/{id}/assign")
    public ApiResponse<WorkOrder> assign(@PathVariable Long id,
                                         @Validated @RequestBody AssignWorkersRequest request) {
        return ApiResponse.ok(orderService.assign(id, request));
    }

    /** 开工：设备停机并生成安全确认清单；并发开工只一个生效。 */
    @PostMapping("/{id}/start")
    public ApiResponse<WorkOrder> start(@PathVariable Long id,
                                        @Validated @RequestBody OrderStartRequest request) {
        return ApiResponse.ok(orderService.start(id, request.getOperator(), request.getRequestId()));
    }

    /** 备件替换：新件序列号装机，旧件序列号强制留存（旧件转报废保留）。 */
    @PostMapping("/{id}/parts/{catalogId}/replace")
    public ApiResponse<PartReplacement> replacePart(@PathVariable Long id,
                                                    @PathVariable Long catalogId,
                                                    @Validated @RequestBody PartReplaceRequest request) {
        return ApiResponse.ok(orderService.replacePart(id, catalogId, request));
    }

    /** 完工：释放未使用的锁定备件，转待验收。 */
    @PostMapping("/{id}/complete")
    public ApiResponse<WorkOrder> complete(@PathVariable Long id,
                                           @RequestParam String operator) {
        return ApiResponse.ok(orderService.complete(id, operator));
    }

    /** 验收：验收人与意见缺一不可。 */
    @PostMapping("/{id}/accept")
    public ApiResponse<WorkOrder> accept(@PathVariable Long id,
                                         @Validated @RequestBody AcceptRequest request) {
        return ApiResponse.ok(orderService.accept(id, request));
    }

    /** 逐项安全确认。 */
    @PostMapping("/{id}/safety-checks/{checkId}/confirm")
    public ApiResponse<SafetyCheck> confirmSafety(@PathVariable Long id,
                                                  @PathVariable Long checkId,
                                                  @Validated @RequestBody SafetyConfirmRequest request) {
        return ApiResponse.ok(orderService.confirmSafety(id, checkId, request));
    }

    /** 关闭工单=设备恢复运行：须验收通过且全部安全确认完成；并发恢复只一个生效。 */
    @PostMapping("/{id}/close")
    public ApiResponse<WorkOrder> close(@PathVariable Long id,
                                        @Validated @RequestBody OrderCloseRequest request) {
        return ApiResponse.ok(orderService.close(id, request.getOperator(), request.getRequestId()));
    }
}
