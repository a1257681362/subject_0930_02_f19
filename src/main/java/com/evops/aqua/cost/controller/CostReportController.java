package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostCarryoverLine;
import com.evops.aqua.cost.service.CostLedgerService;
import com.evops.aqua.cost.service.CostReportService;
import com.evops.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 养殖成本分摊与批次结转：页面视图与 CSV 导出同一查询同一口径（默认只统计 ACTIVE 池塘）。
 * role=FINANCE 财务看金额；role=PRODUCTION（默认）生产只看汇总，金额脱敏。
 */
@RestController
@RequestMapping("/api/aqua/cost")
public class CostReportController {

    private final CostReportService reportService;
    private final CostLedgerService ledgerService;

    public CostReportController(CostReportService reportService, CostLedgerService ledgerService) {
        this.reportService = reportService;
        this.ledgerService = ledgerService;
    }

    /** 成本分摊与批次结转页面数据（后台聚合同口径）。 */
    @GetMapping("/allocation-report")
    public ApiResponse<Map<String, Object>> report(@RequestParam(defaultValue = "PRODUCTION") String role,
                                                   @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResponse.ok(reportService.pageView(role, includeDisabled));
    }

    /** 单批次成本详情：财务看金额与凭证，生产只看单据汇总。 */
    @GetMapping("/batches/{batchId}")
    public ApiResponse<Map<String, Object>> batchDetail(@PathVariable Long batchId,
                                                        @RequestParam(defaultValue = "PRODUCTION") String role) {
        return ApiResponse.ok(reportService.batchDetail(batchId, role));
    }

    /** 出池结转快照（不可变）。 */
    @GetMapping("/batches/{batchId}/carryover")
    public ApiResponse<Map<String, Object>> carryover(@PathVariable Long batchId,
                                                      @RequestParam(defaultValue = "FINANCE") String role) {
        CostCarryover carryover = ledgerService.getCarryoverByBatch(batchId);
        Map<String, Object> view = new LinkedHashMap<>();
        if (carryover == null) {
            view.put("carriedOver", false);
            return ApiResponse.ok(view);
        }
        view.put("carriedOver", true);
        view.put("carryoverNo", carryover.getCarryoverNo());
        view.put("harvestDate", carryover.getHarvestDate());
        view.put("quantity", carryover.getQuantity());
        view.put("pondStatusAtCarryover", carryover.getPondStatusAtCarryover());
        List<CostCarryoverLine> lines = ledgerService.listCarryoverLines(carryover.getId());
        boolean finance = CostLedgerService.ROLE_FINANCE.equalsIgnoreCase(role);
        if (finance) {
            view.put("totalAmount", carryover.getTotalAmount());
            view.put("lines", lines);
            view.put("snapshotJson", carryover.getSnapshotJson());
        } else {
            // 生产视图：只给每类单据数，不给金额
            java.util.List<Map<String, Object>> masked = new java.util.ArrayList<>();
            for (CostCarryoverLine line : lines) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("costType", line.getCostType());
                m.put("entryCount", line.getEntryCount());
                masked.add(m);
            }
            view.put("lines", masked);
        }
        return ApiResponse.ok(view);
    }

    /**
     * CSV 导出：与页面同一 loadRows 查询、同一无效池过滤口径、同一角色金额脱敏口径。
     */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "FINANCE") String role,
                                         @RequestParam(defaultValue = "false") boolean includeDisabled) {
        String csv = reportService.exportCsv(role, includeDisabled);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        String filename = "cost-allocation-" + role.toLowerCase()
                + (includeDisabled ? "-all-pools" : "-active-pools") + ".csv";
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
    }
}
