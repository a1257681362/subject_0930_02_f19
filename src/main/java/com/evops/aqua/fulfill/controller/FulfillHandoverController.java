package com.evops.aqua.fulfill.controller;

import com.evops.aqua.fulfill.dto.HandoverCreateRequest;
import com.evops.aqua.fulfill.dto.HandoverSignRequest;
import com.evops.aqua.fulfill.entity.FulfillHandover;
import com.evops.aqua.fulfill.service.FulfillHandoverService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 交接/签收入口：交接（超差逐箱冻结）、客户首签。
 */
@RestController
@RequestMapping("/api/aqua/fulfill")
public class FulfillHandoverController {

    private final FulfillHandoverService handoverService;

    public FulfillHandoverController(FulfillHandoverService handoverService) {
        this.handoverService = handoverService;
    }

    @PostMapping("/deliveries/{deliveryId}/handovers")
    public ApiResponse<FulfillHandover> create(@PathVariable Long deliveryId,
                                               @Validated @RequestBody HandoverCreateRequest request) {
        return ApiResponse.ok(handoverService.create(deliveryId, request));
    }

    @PostMapping("/handovers/{id}/sign")
    public ApiResponse<FulfillHandover> sign(@PathVariable Long id,
                                             @Validated @RequestBody HandoverSignRequest request) {
        return ApiResponse.ok(handoverService.sign(id, request));
    }
}
