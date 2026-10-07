package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.dto.VoucherCreateRequest;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.service.CostLedgerService;
import com.evops.aqua.cost.service.CostVoucherService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 财务凭证登记薄入口。凭证金额仅财务可见，生产角色只看编号/类型/日期等登记信息。
 */
@RestController
@RequestMapping("/api/aqua/cost/vouchers")
public class CostVoucherController {

    private final CostVoucherService voucherService;

    public CostVoucherController(CostVoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping
    public ApiResponse<CostVoucher> create(@Validated @RequestBody VoucherCreateRequest request) {
        return ApiResponse.ok(voucherService.create(request));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "FINANCE") String role) {
        boolean finance = CostLedgerService.ROLE_FINANCE.equalsIgnoreCase(role);
        List<Map<String, Object>> view = new ArrayList<>();
        for (CostVoucher voucher : voucherService.list()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("voucherNo", voucher.getVoucherNo());
            map.put("voucherType", voucher.getVoucherType());
            map.put("voucherDate", voucher.getVoucherDate());
            map.put("summary", voucher.getSummary());
            map.put("status", voucher.getStatus());
            if (finance) {
                map.put("amount", voucher.getAmount());
            }
            view.add(map);
        }
        return ApiResponse.ok(view);
    }
}
