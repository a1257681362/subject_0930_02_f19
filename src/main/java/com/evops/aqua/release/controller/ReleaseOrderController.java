package com.evops.aqua.release.controller;

import com.evops.aqua.release.dto.ReleaseOrderCreateRequest;
import com.evops.aqua.release.dto.ReleaseResultEnterRequest;
import com.evops.aqua.release.dto.ReleaseReturnRequest;
import com.evops.aqua.release.dto.ReleaseRoundSubmitRequest;
import com.evops.aqua.release.dto.ReleaseSignRequest;
import com.evops.aqua.release.entity.ReleaseOrder;
import com.evops.aqua.release.service.ReleaseOrderService;
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
 * 出池检验单：建单（冻结品种+用途阈值版本）→ 录入结果（系统比对阈值）
 * → 提交（生成放行建议、签发与样品一一对应的报告）→ 退回补检（保留初检、另开复检轮）
 * → 授权签字人电子签名。签名后批次方可进入发货（见发货接口）。
 */
@RestController
@RequestMapping("/api/aqua/release/orders")
public class ReleaseOrderController {

    private final ReleaseOrderService orderService;

    public ReleaseOrderController(ReleaseOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ApiResponse<ReleaseOrder> create(@Validated @RequestBody ReleaseOrderCreateRequest request) {
        return ApiResponse.ok(orderService.create(request));
    }

    @GetMapping
    public ApiResponse<List<ReleaseOrder>> list(@RequestParam(required = false) Long batchId,
                                                @RequestParam(required = false) Long pondId,
                                                @RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.listOrders(batchId, pondId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.detail(id));
    }

    /** 实验室录入检验结果：逐指标按阈值闭区间判定合格 */
    @PostMapping("/{id}/results")
    public ApiResponse<ReleaseOrder> enterResults(@PathVariable Long id,
                                                  @Validated @RequestBody ReleaseResultEnterRequest request) {
        return ApiResponse.ok(orderService.enterResults(id, request));
    }

    /** 提交当前检验轮次：必检齐全，系统比对阈值生成放行建议并签发报告；重复提交幂等 */
    @PostMapping("/{id}/submit")
    public ApiResponse<ReleaseOrder> submit(@PathVariable Long id,
                                            @Validated @RequestBody ReleaseRoundSubmitRequest request) {
        return ApiResponse.ok(orderService.submitRound(id, request));
    }

    /** 退回补检：原因/操作人必填；原结论、原因、操作时间保留，另开复检轮（复检不覆盖初检） */
    @PostMapping("/{id}/return")
    public ApiResponse<ReleaseOrder> returnForReinspection(@PathVariable Long id,
                                                           @Validated @RequestBody ReleaseReturnRequest request) {
        return ApiResponse.ok(orderService.returnForReinspection(id, request));
    }

    /** 授权签字人工号+口令电子签名：仅放行建议 PASS 可签，签名后批次方可发货；重复签名幂等 */
    @PostMapping("/{id}/sign")
    public ApiResponse<ReleaseOrder> sign(@PathVariable Long id,
                                          @Validated @RequestBody ReleaseSignRequest request) {
        return ApiResponse.ok(orderService.sign(id, request));
    }

    /** 样品编号↔检测报告一一对应核对 */
    @GetMapping("/{id}/sample-report-mapping")
    public ApiResponse<Map<String, Object>> sampleReportMapping(@PathVariable Long id) {
        return ApiResponse.ok(orderService.sampleReportCorrespondence(id));
    }
}
