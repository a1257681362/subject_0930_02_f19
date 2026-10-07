package com.evops.aqua.disease.controller;

import com.evops.aqua.disease.dto.ObservationCreateRequest;
import com.evops.aqua.disease.dto.DispatchRequest;
import com.evops.aqua.disease.entity.DiseaseIsolation;
import com.evops.aqua.disease.entity.DiseaseObservation;
import com.evops.aqua.disease.service.DiseaseIsolationService;
import com.evops.aqua.disease.service.DiseaseObservationService;
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
 * 疫病观察单：发现异常症状生成观察单（幂等）→ 按风险等级推荐隔离池 → 调度到隔离池。
 */
@RestController
@RequestMapping("/api/aqua/disease/observations")
public class DiseaseObservationController {

    private final DiseaseObservationService observationService;
    private final DiseaseIsolationService isolationService;

    public DiseaseObservationController(DiseaseObservationService observationService,
                                        DiseaseIsolationService isolationService) {
        this.observationService = observationService;
        this.isolationService = isolationService;
    }

    @PostMapping
    public ApiResponse<DiseaseObservation> create(@Validated @RequestBody ObservationCreateRequest request) {
        return ApiResponse.ok(observationService.create(request));
    }

    @GetMapping
    public ApiResponse<List<DiseaseObservation>> list(@RequestParam(required = false) Long pondId,
                                                      @RequestParam(required = false) Long batchId,
                                                      @RequestParam(required = false) String status) {
        return ApiResponse.ok(observationService.list(pondId, batchId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiseaseObservation> detail(@PathVariable Long id) {
        return ApiResponse.ok(observationService.getOrThrow(id));
    }

    /** 按风险等级推荐隔离池：可用清单 + 被排除候选及各自冲突原因。 */
    @GetMapping("/{id}/recommend-pools")
    public ApiResponse<Map<String, Object>> recommend(
            @PathVariable Long id,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dispatchDate) {
        return ApiResponse.ok(observationService.recommend(id, dispatchDate));
    }

    /** 调度到隔离池：转移前校验容量/消毒/调入权限/混养，失败说明全部冲突原因。 */
    @PostMapping("/{id}/dispatch")
    public ApiResponse<DiseaseIsolation> dispatch(@PathVariable Long id,
                                                  @Validated @RequestBody DispatchRequest request) {
        return ApiResponse.ok(isolationService.dispatch(id, request));
    }
}
