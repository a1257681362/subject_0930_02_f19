package com.evops.aqua.med.controller;

import com.evops.aqua.med.dto.RequisitionActionRequest;
import com.evops.aqua.med.dto.RequisitionCreateRequest;
import com.evops.aqua.med.dto.RequisitionResubmitRequest;
import com.evops.aqua.med.dto.RequisitionReviewRequest;
import com.evops.aqua.med.entity.MedRequisition;
import com.evops.aqua.med.service.MedRequisitionService;
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
 * 用药领用单入口：创建/提交/审核/驳回/撤回，角色视图与池塘休药锁定查询。
 */
@RestController
@RequestMapping("/api/aqua/med/requisitions")
public class MedRequisitionController {

    private final MedRequisitionService requisitionService;

    public MedRequisitionController(MedRequisitionService requisitionService) {
        this.requisitionService = requisitionService;
    }

    @PostMapping
    public ApiResponse<MedRequisition> create(@Validated @RequestBody RequisitionCreateRequest request) {
        return ApiResponse.ok(requisitionService.create(request));
    }

    @GetMapping
    public ApiResponse<List<MedRequisition>> list(@RequestParam(required = false) Long pondId,
                                                  @RequestParam(required = false) String status) {
        return ApiResponse.ok(requisitionService.list(pondId, status));
    }

    /** 角色视图：role=VET 看处方明细；其他（含 KEEPER）只看库存字段。 */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> view(@PathVariable Long id,
                                                 @RequestParam(required = false) String role) {
        return ApiResponse.ok(requisitionService.view(id, role));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<MedRequisition> submit(@PathVariable Long id,
                                              @Validated @RequestBody RequisitionActionRequest request) {
        return ApiResponse.ok(requisitionService.submit(id, request));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<MedRequisition> approve(@PathVariable Long id,
                                               @Validated @RequestBody RequisitionReviewRequest request) {
        return ApiResponse.ok(requisitionService.approve(id, request));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<MedRequisition> reject(@PathVariable Long id,
                                              @Validated @RequestBody RequisitionReviewRequest request) {
        return ApiResponse.ok(requisitionService.reject(id, request));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<MedRequisition> withdraw(@PathVariable Long id,
                                                @Validated @RequestBody RequisitionActionRequest request) {
        return ApiResponse.ok(requisitionService.withdraw(id, request));
    }

    /** 撤回后重提：另立新版本领用单，旧撤回单与历史审核结论原样保留。 */
    @PostMapping("/{id}/resubmit")
    public ApiResponse<MedRequisition> resubmit(@PathVariable Long id,
                                                @Validated @RequestBody RequisitionResubmitRequest request) {
        return ApiResponse.ok(requisitionService.resubmit(id, request));
    }
}
