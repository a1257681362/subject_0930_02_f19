package com.evops.aqua.report.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.report.dto.SeaGrantUpsertRequest;
import com.evops.aqua.report.entity.ReportChangeEvidence;
import com.evops.aqua.report.entity.ReportSeaGrant;
import com.evops.aqua.report.mapper.ReportSeaGrantMapper;
import com.evops.aqua.report.service.ReportEvidenceService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 监管月报管理：用户海区数据权限登记与模块证据追溯。
 * 海区授权是查询/导出过滤的唯一行源；登记/调整均留证。
 */
@RestController
@RequestMapping("/api/aqua/report")
public class ReportAdminController {

    private final ReportSeaGrantMapper grantMapper;
    private final ReportEvidenceService evidenceService;

    public ReportAdminController(ReportSeaGrantMapper grantMapper,
                                 ReportEvidenceService evidenceService) {
        this.grantMapper = grantMapper;
        this.evidenceService = evidenceService;
    }

    /** 登记/调整用户授权海区（逗号分隔，* 为全部海区）。 */
    @PostMapping("/sea-grants")
    public ApiResponse<ReportSeaGrant> upsertGrant(@Validated @RequestBody SeaGrantUpsertRequest request) {
        ReportSeaGrant existing = grantMapper.selectOne(new QueryWrapper<ReportSeaGrant>()
                .eq("username", request.getUsername()).last("limit 1"));
        ReportSeaGrant before = existing == null ? null : copyOf(existing);
        if (existing == null) {
            existing = new ReportSeaGrant();
            existing.setUsername(request.getUsername());
            existing.setSeaAreas(request.getSeaAreas());
            existing.setRemark(request.getRemark());
            grantMapper.insert(existing);
        } else {
            existing.setSeaAreas(request.getSeaAreas());
            existing.setRemark(request.getRemark());
            grantMapper.updateById(existing);
        }
        evidenceService.record(ReportChangeEvidence.ENTITY_SEA_GRANT, existing.getId(),
                ReportChangeEvidence.ACTION_GRANT, null, ReportEvidenceService.currentOperator(),
                before, existing, "登记/调整用户海区数据权限：" + request.getUsername()
                        + " -> " + request.getSeaAreas());
        return ApiResponse.ok(existing);
    }

    @GetMapping("/sea-grants")
    public ApiResponse<List<ReportSeaGrant>> listGrants() {
        return ApiResponse.ok(grantMapper.selectList(new QueryWrapper<ReportSeaGrant>()
                .orderByAsc("id")));
    }

    /** 证据追溯：按对象类型/对象ID/请求号查询，只追加。 */
    @GetMapping("/evidence")
    public ApiResponse<List<ReportChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                            @RequestParam(required = false) Long entityId,
                                                            @RequestParam(required = false) String requestId) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId));
    }

    private ReportSeaGrant copyOf(ReportSeaGrant source) {
        ReportSeaGrant copy = new ReportSeaGrant();
        copy.setId(source.getId());
        copy.setUsername(source.getUsername());
        copy.setSeaAreas(source.getSeaAreas());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
