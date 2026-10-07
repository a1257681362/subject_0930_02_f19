package com.evops.aqua.feed.controller;

import com.evops.aqua.feed.dto.FeedOrderActionRequest;
import com.evops.aqua.feed.dto.OrderPrepareRequest;
import com.evops.aqua.feed.dto.ReductionRequest;
import com.evops.aqua.feed.entity.FeedOrder;
import com.evops.aqua.feed.entity.FeedOrderItem;
import com.evops.aqua.feed.service.FeedOrderService;
import com.evops.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 投喂单：准备（按苗种/日龄/水温命中配方并冻结版本快照）→ 锁定（占用批号）
 * → 临时减量（原因+审批人）→ 执行（转消耗）。只有锁定后才允许执行。
 */
@RestController
@RequestMapping("/api/aqua/feed/orders")
public class FeedOrderController {

    private final FeedOrderService orderService;

    public FeedOrderController(FeedOrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/prepare")
    public ApiResponse<FeedOrder> prepare(@Validated @RequestBody OrderPrepareRequest request) {
        return ApiResponse.ok(orderService.prepare(request));
    }

    @PostMapping("/{id}/lock")
    public ApiResponse<FeedOrder> lock(@PathVariable Long id,
                                       @RequestBody(required = false) FeedOrderActionRequest request) {
        String requestId = request == null ? null : request.getRequestId();
        return ApiResponse.ok(orderService.lock(id, requestId));
    }

    @PostMapping("/{id}/unlock")
    public ApiResponse<FeedOrder> unlock(@PathVariable Long id,
                                         @RequestBody(required = false) FeedOrderActionRequest request) {
        String requestId = request == null ? null : request.getRequestId();
        return ApiResponse.ok(orderService.unlock(id, requestId));
    }

    @PostMapping("/{id}/items/{itemId}/reduction")
    public ApiResponse<FeedOrderItem> reduce(@PathVariable Long id,
                                             @PathVariable Long itemId,
                                             @Validated @RequestBody ReductionRequest request) {
        return ApiResponse.ok(orderService.reduce(id, itemId, request));
    }

    @PostMapping("/{id}/execute")
    public ApiResponse<FeedOrder> execute(@PathVariable Long id,
                                          @RequestBody(required = false) FeedOrderActionRequest request) {
        String requestId = request == null ? null : request.getRequestId();
        return ApiResponse.ok(orderService.execute(id, requestId));
    }

    @GetMapping
    public ApiResponse<List<FeedOrder>> list(@RequestParam(required = false) Long pondId,
                                             @RequestParam(required = false) Long batchId,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String feedType) {
        return ApiResponse.ok(orderService.listOrders(pondId, batchId, status, feedType));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        return ApiResponse.ok(orderService.orderDetail(id));
    }

    @GetMapping("/daily-summary")
    public ApiResponse<Map<String, Object>> dailySummary(@RequestParam Long pondId,
                                                         @RequestParam
                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                                                         LocalDate feedDate) {
        return ApiResponse.ok(orderService.dailySummary(pondId, feedDate));
    }
}
