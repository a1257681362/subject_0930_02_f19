package com.evops.aqua.release.controller;

import com.evops.aqua.release.dto.ReleaseShipRequest;
import com.evops.aqua.release.entity.ReleaseShipment;
import com.evops.aqua.release.service.ReleaseShippingService;
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

/**
 * 批次发货登记：进入发货的唯一出口。
 * 硬门——检验单必须已授权电子签名（SIGNED）才允许发货；一单一批发货一次，requestId 幂等。
 */
@RestController
@RequestMapping("/api/aqua/release")
public class ReleaseShipmentController {

    private final ReleaseShippingService shippingService;

    public ReleaseShipmentController(ReleaseShippingService shippingService) {
        this.shippingService = shippingService;
    }

    @PostMapping("/orders/{id}/ship")
    public ApiResponse<ReleaseShipment> ship(@PathVariable Long id,
                                             @Validated @RequestBody ReleaseShipRequest request) {
        return ApiResponse.ok(shippingService.ship(id, request));
    }

    @GetMapping("/shipments")
    public ApiResponse<List<ReleaseShipment>> list(@RequestParam(required = false) Long batchId,
                                                   @RequestParam(required = false) Long orderId) {
        return ApiResponse.ok(shippingService.listShipments(batchId, orderId));
    }
}
