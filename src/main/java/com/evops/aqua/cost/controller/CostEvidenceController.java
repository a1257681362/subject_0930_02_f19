package com.evops.aqua.cost.controller;

import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.service.CostEvidenceService;
import com.evops.aqua.cost.service.CostLedgerService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 成本模块变更证据追溯：归集/转池分配/出池结转/差异审核/池塘停用全部可按对象或 requestId 查。
 * 证据中的前后快照与文字含金额，仅财务可查全量；生产角色只看动作/对象/操作者等流程信息。
 */
@RestController
@RequestMapping("/api/aqua/cost/evidence")
public class CostEvidenceController {

    private final CostEvidenceService evidenceService;

    public CostEvidenceController(CostEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String entityType,
                                                       @RequestParam(required = false) Long entityId,
                                                       @RequestParam(required = false) String requestId,
                                                       @RequestParam(defaultValue = "FINANCE") String role) {
        boolean finance = CostLedgerService.ROLE_FINANCE.equalsIgnoreCase(role);
        List<Map<String, Object>> view = new ArrayList<>();
        for (CostChangeEvidence evidence : evidenceService.list(entityType, entityId, requestId)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entityType", evidence.getEntityType());
            map.put("entityId", evidence.getEntityId());
            map.put("action", evidence.getAction());
            map.put("requestId", evidence.getRequestId());
            map.put("operator", evidence.getOperator());
            if (finance) {
                map.put("detail", evidence.getDetail());
                map.put("beforeJson", evidence.getBeforeJson());
                map.put("afterJson", evidence.getAfterJson());
            }
            view.add(map);
        }
        return ApiResponse.ok(view);
    }
}
