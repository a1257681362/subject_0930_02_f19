package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.dto.CostEntryCreateRequest;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.service.CostLedgerService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 养殖成本费用归集入口：饲料/人工/药品/能源按池按批补录，必须引用财务凭证。
 */
@RestController
@RequestMapping("/api/aqua/cost/entries")
public class CostEntryController {

    private final CostLedgerService ledgerService;

    public CostEntryController(CostLedgerService ledgerService) {
        this.ledgerService = ledgerService;
    }

    @PostMapping
    public ApiResponse<CostEntry> create(@Validated @RequestBody CostEntryCreateRequest request) {
        return ApiResponse.ok(ledgerService.createEntry(request));
    }

    /** 列表按角色脱敏：role=FINANCE 看金额凭证；PRODUCTION（默认）只看汇总。 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) Long pondId,
                                                       @RequestParam(required = false) Long batchId,
                                                       @RequestParam(required = false) String costType,
                                                       @RequestParam(defaultValue = "PRODUCTION") String role) {
        return ApiResponse.ok(ledgerService.listEntriesView(pondId, batchId, costType, role));
    }
}
