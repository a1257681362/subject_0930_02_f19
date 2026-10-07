package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.dto.ObservationCreateRequest;
import com.evops.aqua.disease.entity.DiseaseChangeEvidence;
import com.evops.aqua.disease.entity.DiseaseObservation;
import com.evops.aqua.disease.entity.PondGuard;
import com.evops.aqua.disease.mapper.DiseaseObservationMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 疫病观察单：发现异常症状即生成（requestId 幂等，重复提交返回同一单）。
 * 按风险等级推荐隔离池：低风险建议原地观察；中风险仅推荐同海区隔离池；
 * 高风险推荐全部可用隔离池（同海区优先）。被排除的候选池逐条说明原因。
 */
@Service
public class DiseaseObservationService {

    private final DiseaseObservationMapper observationMapper;
    private final SeedlingBatchService seedlingBatchService;
    private final PondService pondService;
    private final PondGuardService pondGuardService;
    private final IsolationPoolEvaluator evaluator;
    private final DiseaseEvidenceService evidenceService;

    public DiseaseObservationService(DiseaseObservationMapper observationMapper,
                                     SeedlingBatchService seedlingBatchService,
                                     PondService pondService,
                                     PondGuardService pondGuardService,
                                     IsolationPoolEvaluator evaluator,
                                     DiseaseEvidenceService evidenceService) {
        this.observationMapper = observationMapper;
        this.seedlingBatchService = seedlingBatchService;
        this.pondService = pondService;
        this.pondGuardService = pondGuardService;
        this.evaluator = evaluator;
        this.evidenceService = evidenceService;
    }

    /**
     * 生成观察单：批次须在养且当前在该池。requestId 幂等，重复提交返回首次观察单。
     */
    @Transactional(rollbackFor = Exception.class)
    public DiseaseObservation create(ObservationCreateRequest request) {
        DiseaseObservation existing = observationMapper.selectOne(
                new QueryWrapper<DiseaseObservation>().eq("request_id", request.getRequestId()));
        if (existing != null) {
            return existing; // 幂等：重复请求返回首次观察单，不产生第二条
        }
        SeedlingBatch batch = seedlingBatchService.getOrThrow(request.getBatchId());
        seedlingBatchService.requireStocked(batch, "登记疫病观察");
        if (!batch.getPondId().equals(request.getPondId())) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "当前不在池塘id=" + request.getPondId() + "，请核对后再登记观察");
        }
        String riskLevel = normalizeRisk(request.getRiskLevel());

        DiseaseObservation observation = new DiseaseObservation();
        observation.setObsNo(nextObsNo(request.getBatchId(), request.getObserveDate()));
        observation.setPondId(request.getPondId());
        observation.setBatchId(request.getBatchId());
        observation.setObserveDate(request.getObserveDate());
        observation.setSymptoms(request.getSymptoms());
        observation.setRiskLevel(riskLevel);
        observation.setStatus(DiseaseObservation.STATUS_OBSERVING);
        observation.setObserver(request.getObserver());
        observation.setRequestId(request.getRequestId());
        observation.setRemark(request.getRemark());
        observationMapper.insert(observation);

        evidenceService.record(DiseaseChangeEvidence.ENTITY_OBSERVATION, observation.getId(),
                DiseaseChangeEvidence.ACTION_CREATE_OBSERVATION, request.getRequestId(), request.getObserver(),
                null, observation,
                "发现异常症状生成观察单：批次" + batch.getBatchNo() + "，症状[" + request.getSymptoms()
                        + "]，风险等级" + riskLevel + "，观察人=" + request.getObserver());
        return observation;
    }

    /**
     * 按风险等级推荐隔离池。
     * LOW：建议原地继续观察，不推荐；MEDIUM：仅同海区隔离池；HIGH：全部可用隔离池，同海区优先。
     * 每个被排除的候选池都附带冲突原因。
     */
    public Map<String, Object> recommend(Long observationId, LocalDate dispatchDate) {
        DiseaseObservation observation = getOrThrow(observationId);
        LocalDate date = dispatchDate == null ? LocalDate.now() : dispatchDate;
        SeedlingBatch batch = seedlingBatchService.getOrThrow(observation.getBatchId());
        Pond sourcePond = pondService.getOrThrow(observation.getPondId());
        int quantity = batch.getQuantity() == null ? 0 : batch.getQuantity();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("observationId", observation.getId());
        result.put("obsNo", observation.getObsNo());
        result.put("riskLevel", observation.getRiskLevel());
        result.put("dispatchDate", date);

        List<Map<String, Object>> recommended = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();
        if (DiseaseObservation.RISK_LOW.equals(observation.getRiskLevel())) {
            result.put("advise", "低风险：建议原地继续观察，暂不调度隔离池");
        } else {
            boolean sameSeaOnly = DiseaseObservation.RISK_MEDIUM.equals(observation.getRiskLevel());
            result.put("advise", sameSeaOnly
                    ? "中风险：推荐同海区隔离池，调入后持续观察"
                    : "高风险：建议立即隔离，以下隔离池可用（同海区优先）");
            for (Pond pond : pondService.list()) {
                if (pond.getId().equals(sourcePond.getId())) {
                    continue; // 原池不作为候选
                }
                PondGuard guard = pondGuardService.getByPond(pond.getId());
                if (guard == null || !PondGuard.ROLE_ISOLATION.equals(guard.getPondRole())) {
                    continue; // 非隔离池不参与推荐，也不列入排除清单
                }
                List<String> reasons = evaluator.evaluate(pond, guard, quantity, date);
                boolean sameSea = pond.getSeaArea() != null && pond.getSeaArea().equals(sourcePond.getSeaArea());
                if (sameSeaOnly && !sameSea) {
                    reasons.add("非同一海区（原池属" + sourcePond.getSeaArea() + "），中风险仅推荐同海区隔离池");
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("pondId", pond.getId());
                item.put("pondCode", pond.getPondCode());
                item.put("pondName", pond.getPondName());
                item.put("seaArea", pond.getSeaArea());
                item.put("sameSeaArea", sameSea);
                item.put("capacity", guard.getCapacity());
                item.put("currentStock", evaluator.currentStock(pond.getId()));
                item.put("disinfectedUntil", guard.getDisinfectedUntil());
                if (reasons.isEmpty()) {
                    recommended.add(item);
                } else {
                    item.put("reasons", reasons);
                    excluded.add(item);
                }
            }
            // 同海区优先
            recommended.sort((a, b) -> Boolean.compare(
                    Boolean.FALSE.equals(a.get("sameSeaArea")), Boolean.FALSE.equals(b.get("sameSeaArea"))));
        }
        result.put("recommended", recommended);
        result.put("excluded", excluded);
        return result;
    }

    public DiseaseObservation getOrThrow(Long id) {
        DiseaseObservation observation = observationMapper.selectById(id);
        if (observation == null) {
            throw new IllegalArgumentException("疫病观察单不存在，id=" + id);
        }
        return observation;
    }

    public List<DiseaseObservation> list(Long pondId, Long batchId, String status) {
        QueryWrapper<DiseaseObservation> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return observationMapper.selectList(wrapper);
    }

    private String normalizeRisk(String riskLevel) {
        if (riskLevel == null || riskLevel.trim().isEmpty()) {
            return DiseaseObservation.RISK_LOW;
        }
        String level = riskLevel.trim().toUpperCase();
        if (!DiseaseObservation.RISK_LOW.equals(level)
                && !DiseaseObservation.RISK_MEDIUM.equals(level)
                && !DiseaseObservation.RISK_HIGH.equals(level)) {
            throw new IllegalArgumentException("风险等级只支持 LOW/MEDIUM/HIGH，收到：" + riskLevel);
        }
        return level;
    }

    private String nextObsNo(Long batchId, LocalDate observeDate) {
        Long count = observationMapper.selectCount(
                new QueryWrapper<DiseaseObservation>().eq("batch_id", batchId));
        long seq = (count == null ? 0 : count) + 1;
        return "OBS-" + batchId + "-" + observeDate.toString().replace("-", "") + "-" + seq;
    }
}
