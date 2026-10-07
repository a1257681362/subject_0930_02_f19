package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.dto.FulfillOrderCreateRequest;
import com.evops.aqua.fulfill.entity.FulfillOrder;
import com.evops.aqua.fulfill.external.ExternalUnavailableException;
import com.evops.aqua.fulfill.service.FulfillOrderService;
import com.evops.aqua.fulfill.service.OutboxService;
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
 * 履约订单入口：创建（同步海水养殖管理后才算确认）、查询。
 * 外部无响应时明确返回失败——订单停留 PENDING_SYNC，绝不假装成功；
 * 同 requestId 重放返回首次订单及其真实同步状态，不重复通知外部。
 */
@RestController
@RequestMapping("/api/aqua/fulfill/orders")
public class FulfillOrderController {

    public static final String SYNC_FAIL_MESSAGE = "海水养殖管理无响应，拆单未确认，未成功";

    private final FulfillOrderService orderService;
    private final OutboxService outboxService;

    public FulfillOrderController(FulfillOrderService orderService, OutboxService outboxService) {
        this.orderService = orderService;
        this.outboxService = outboxService;
    }

    @PostMapping
    public ApiResponse<FulfillOrder> create(@Validated @RequestBody FulfillOrderCreateRequest request) {
        // 重放路径：已存在首次订单时直接返回其真实状态，绝不再次触发外部通知（恢复只由 scan/Quartz 驱动）
        FulfillOrder existing = orderService.findByRequestId(request.getRequestId());
        if (existing != null) {
            return ApiResponse.ok(existing);
        }
        FulfillOrder order = orderService.create(request);
        // 仅首次创建路径在业务事务提交后、事务外尝试即时确认
        if (FulfillOrder.STATUS_PENDING_SYNC.equals(order.getStatus())) {
            try {
                outboxService.dispatchForOrder(order.getId());
            } catch (ExternalUnavailableException ex) {
                order = orderService.getOrThrow(order.getId());
                return ApiResponse.fail(SYNC_FAIL_MESSAGE);
            }
            order = orderService.getOrThrow(order.getId());
        }
        return ApiResponse.ok(order);
    }

    @GetMapping
    public ApiResponse<List<FulfillOrder>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(orderService.list(status));
    }

    /**
     * 订单视图：role=INTERNAL 看全量（含金额口径）；缺省/其它值按 CUSTOMER 最小可见白名单，
     * 只含签收、差异与数量信息，不含单价/金额/成本/温度/冷库。
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> view(@PathVariable Long id,
                                                  @RequestParam(required = false) String role) {
        return ApiResponse.ok(orderService.view(id, role));
    }
}
