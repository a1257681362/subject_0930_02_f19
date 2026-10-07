package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.dto.DispatchRequest;
import com.evops.aqua.disease.dto.ReleaseRequest;
import com.evops.aqua.disease.entity.DiseaseChangeEvidence;
import com.evops.aqua.disease.entity.DiseaseIsolation;
import com.evops.aqua.disease.entity.DiseaseObservation;
import com.evops.aqua.disease.entity.DiseaseStockMovement;
import com.evops.aqua.disease.entity.PondGuard;
import com.evops.aqua.disease.mapper.DiseaseIsolationMapper;
import com.evops.aqua.disease.mapper.DiseaseObservationMapper;
import com.evops.aqua.disease.mapper.DiseaseStockMovementMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.entity.TransferRecord;
import com.evops.aqua.event.BatchTransferredEvent;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.mapper.TransferRecordMapper;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 隔离调度：观察单 → 校验（容量/消毒/调入权限/混养）→ 调入隔离池 → 兽医复核解除 → 回原池。
 * 调度失败汇总全部冲突原因并留证；原池与隔离池之间的数量变化逐笔记录（只追加）；
 * 调度与解除均按 requestId 幂等，重复请求不多写一笔账。
 */
@Service
public class DiseaseIsolationService {

    private final DiseaseIsolationMapper isolationMapper;
    private final DiseaseObservationMapper observationMapper;
    private final DiseaseStockMovementMapper movementMapper;
    private final DiseaseObservationService observationService;
    private final PondGuardService pondGuardService;
    private final IsolationPoolEvaluator evaluator;
    private final DiseaseEvidenceService evidenceService;
    private final SeedlingBatchService seedlingBatchService;
    private final SeedlingBatchMapper batchMapper;
    private final TransferRecordMapper transferRecordMapper;
    private final PondService pondService;
    private final ApplicationEventPublisher eventPublisher;

    public DiseaseIsolationService(DiseaseIsolationMapper isolationMapper,
                                   DiseaseObservationMapper observationMapper,
                                   DiseaseStockMovementMapper movementMapper,
                                   DiseaseObservationService observationService,
                                   PondGuardService pondGuardService,
                                   IsolationPoolEvaluator evaluator,
                                   DiseaseEvidenceService evidenceService,
                                   SeedlingBatchService seedlingBatchService,
                                   SeedlingBatchMapper batchMapper,
                                   TransferRecordMapper transferRecordMapper,
                                   PondService pondService,
                                   ApplicationEventPublisher eventPublisher) {
        this.isolationMapper = isolationMapper;
        this.observationMapper = observationMapper;
        this.movementMapper = movementMapper;
        this.observationService = observationService;
        this.pondGuardService = pondGuardService;
        this.evaluator = evaluator;
        this.evidenceService = evidenceService;
        this.seedlingBatchService = seedlingBatchService;
        this.batchMapper = batchMapper;
        this.transferRecordMapper = transferRecordMapper;
        this.pondService = pondService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 调度到隔离池：转移前校验池容量、消毒状态、目标池调入权限与混养占用。
     * 任一不满足即失败，错误信息汇总全部冲突原因，并写 DISPATCH_FAILED 证据。
     * 成功则生成隔离单 + 调入数量账 + 台账转移记录，观察单转 ISOLATED。
     */
    @Transactional(rollbackFor = Exception.class)
    public DiseaseIsolation dispatch(Long observationId, DispatchRequest request) {
        DiseaseIsolation duplicated = isolationMapper.selectOne(
                new QueryWrapper<DiseaseIsolation>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated; // 幂等：重复调度返回首次隔离单，不多写记录
        }
        DiseaseObservation observation = observationService.getOrThrow(observationId);
        if (DiseaseObservation.STATUS_ISOLATED.equals(observation.getStatus())) {
            return isolationMapper.selectOne(
                    new QueryWrapper<DiseaseIsolation>().eq("observation_id", observationId));
        }
        if (!DiseaseObservation.STATUS_OBSERVING.equals(observation.getStatus())) {
            throw new IllegalArgumentException(
                    "观察单" + observation.getObsNo() + "当前状态" + observation.getStatus() + "，不能调度隔离");
        }
        SeedlingBatch batch = seedlingBatchService.getOrThrow(observation.getBatchId());
        seedlingBatchService.requireStocked(batch, "调度隔离");
        if (!batch.getPondId().equals(observation.getPondId())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo()
                    + "已不在观察单登记的原池，请核实后再调度");
        }
        Pond target = pondService.getOrThrow(request.getToPondId());
        if (target.getId().equals(observation.getPondId())) {
            throw new IllegalArgumentException("目标池与原池相同，无需隔离调度");
        }
        LocalDate dispatchDate = request.getDispatchDate() == null
                ? LocalDate.now() : request.getDispatchDate();
        int quantity = batch.getQuantity() == null ? 0 : batch.getQuantity();

        // 转移前校验：容量、消毒状态、目标池权限、混养占用——收集全部冲突原因一次说明
        PondGuard guard = pondGuardService.getByPond(target.getId());
        List<String> conflicts = evaluator.evaluate(target, guard, quantity, dispatchDate);
        if (!conflicts.isEmpty()) {
            String detail = "隔离调度失败，冲突原因：" + String.join("；", conflicts);
            // 失败证据独立事务提交：业务回滚但冲突原因必须留下
            evidenceService.recordStandalone(DiseaseChangeEvidence.ENTITY_OBSERVATION, observation.getId(),
                    DiseaseChangeEvidence.ACTION_DISPATCH_FAILED, request.getRequestId(),
                    request.getOperator(), observation, null, detail);
            throw new IllegalArgumentException(detail);
        }

        DiseaseIsolation isolation = new DiseaseIsolation();
        isolation.setIsolationNo("ISO-" + observation.getObsNo());
        isolation.setObservationId(observation.getId());
        isolation.setBatchId(batch.getId());
        isolation.setFromPondId(observation.getPondId());
        isolation.setToPondId(target.getId());
        isolation.setQuantity(quantity);
        isolation.setStatus(DiseaseIsolation.STATUS_ISOLATED);
        isolation.setIsolatedBy(request.getOperator());
        isolation.setIsolatedTime(LocalDateTime.now());
        isolation.setRequestId(request.getRequestId());
        isolation.setRemark(request.getRemark());
        isolationMapper.insert(isolation);

        // 数量变化逐笔记录：原池 -> 隔离池
        recordMovement(isolation, DiseaseStockMovement.TYPE_ISOLATE_IN,
                isolation.getFromPondId(), isolation.getToPondId(), quantity,
                request.getOperator(), request.getRequestId(),
                "疫病隔离调入隔离池" + target.getPondCode());
        // 台账同步：批次所在池更新 + 台账转移记录，保证批次全链路追溯不断
        recordLedgerTransfer(batch, isolation.getFromPondId(), isolation.getToPondId(), quantity,
                dispatchDate, "疫病隔离调度：" + isolation.getIsolationNo());

        observation.setStatus(DiseaseObservation.STATUS_ISOLATED);
        observationMapper.updateById(observation);

        evidenceService.record(DiseaseChangeEvidence.ENTITY_ISOLATION, isolation.getId(),
                DiseaseChangeEvidence.ACTION_DISPATCH, request.getRequestId(), request.getOperator(),
                null, isolation,
                "隔离调度成功：批次" + batch.getBatchNo() + "共" + quantity + "尾，原池id="
                        + isolation.getFromPondId() + " -> 隔离池" + target.getPondCode()
                        + "，调度人=" + request.getOperator());
        return isolation;
    }

    /**
     * 解除隔离：必须由兽医复核（复核兽医与复核意见必填）。
     * 批次数量逐笔记回原池；重复解除幂等返回，不重复写数量账。
     */
    @Transactional(rollbackFor = Exception.class)
    public DiseaseIsolation release(Long isolationId, ReleaseRequest request) {
        DiseaseIsolation isolation = getOrThrow(isolationId);
        if (DiseaseIsolation.STATUS_RELEASED.equals(isolation.getStatus())) {
            return isolation; // 幂等：重复解除返回同一结果，不重复写数量账
        }
        DiseaseStockMovement duplicated = movementMapper.selectOne(
                new QueryWrapper<DiseaseStockMovement>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return isolation;
        }
        SeedlingBatch batch = seedlingBatchService.getOrThrow(isolation.getBatchId());
        seedlingBatchService.requireStocked(batch, "解除隔离");
        if (!batch.getPondId().equals(isolation.getToPondId())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo()
                    + "当前不在隔离池id=" + isolation.getToPondId() + "，数据异常，请人工核查");
        }
        Pond origin = pondService.getOrThrow(isolation.getFromPondId());

        DiseaseIsolation before = copyOf(isolation);
        LocalDate today = LocalDate.now();
        // 数量变化逐笔记录：隔离池 -> 原池，操作者为复核兽医
        recordMovement(isolation, DiseaseStockMovement.TYPE_RELEASE_BACK,
                isolation.getToPondId(), isolation.getFromPondId(), isolation.getQuantity(),
                request.getVetName(), request.getRequestId(),
                "兽医复核解除隔离，回原池" + origin.getPondCode());
        recordLedgerTransfer(batch, isolation.getToPondId(), isolation.getFromPondId(),
                isolation.getQuantity(), today, "解除隔离回原池：" + isolation.getIsolationNo());

        isolation.setStatus(DiseaseIsolation.STATUS_RELEASED);
        isolation.setVetName(request.getVetName());
        isolation.setVetReviewRemark(request.getReviewRemark());
        isolation.setReleasedTime(LocalDateTime.now());
        isolation.setReleaseRequestId(request.getRequestId());
        isolationMapper.updateById(isolation);

        DiseaseObservation observation = observationService.getOrThrow(isolation.getObservationId());
        observation.setStatus(DiseaseObservation.STATUS_RELEASED);
        observationMapper.updateById(observation);

        evidenceService.record(DiseaseChangeEvidence.ENTITY_ISOLATION, isolation.getId(),
                DiseaseChangeEvidence.ACTION_RELEASE, request.getRequestId(), request.getVetName(),
                before, isolation,
                "兽医复核解除隔离：批次" + batch.getBatchNo() + "共" + isolation.getQuantity()
                        + "尾回原池" + origin.getPondCode() + "，复核兽医=" + request.getVetName()
                        + "，复核意见[" + request.getReviewRemark() + "]");
        return isolation;
    }

    public DiseaseIsolation getOrThrow(Long id) {
        DiseaseIsolation isolation = isolationMapper.selectById(id);
        if (isolation == null) {
            throw new IllegalArgumentException("隔离单不存在，id=" + id);
        }
        return isolation;
    }

    public List<DiseaseIsolation> list(Long batchId, String status) {
        QueryWrapper<DiseaseIsolation> wrapper = new QueryWrapper<>();
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return isolationMapper.selectList(wrapper);
    }

    /** 数量变化逐笔账：按批次或隔离单查询，原池与隔离池之间每笔调入调出都可追。 */
    public List<DiseaseStockMovement> listMovements(Long batchId, Long isolationId) {
        QueryWrapper<DiseaseStockMovement> wrapper = new QueryWrapper<>();
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(isolationId != null, "isolation_id", isolationId);
        wrapper.orderByAsc("id");
        return movementMapper.selectList(wrapper);
    }

    private void recordMovement(DiseaseIsolation isolation, String moveType,
                                Long fromPondId, Long toPondId, int quantity,
                                String operator, String requestId, String remark) {
        DiseaseStockMovement movement = new DiseaseStockMovement();
        movement.setIsolationId(isolation.getId());
        movement.setBatchId(isolation.getBatchId());
        movement.setMoveType(moveType);
        movement.setFromPondId(fromPondId);
        movement.setToPondId(toPondId);
        movement.setQuantity(quantity);
        movement.setOperator(operator);
        movement.setRequestId(requestId);
        movement.setRemark(remark);
        movementMapper.insert(movement);
    }

    private void recordLedgerTransfer(SeedlingBatch batch, Long fromPondId, Long toPondId,
                                      int quantity, LocalDate date, String reason) {
        TransferRecord record = new TransferRecord();
        record.setBatchId(batch.getId());
        record.setFromPondId(fromPondId);
        record.setToPondId(toPondId);
        record.setTransferDate(date);
        record.setQuantity(quantity);
        record.setBiomassKg(batch.getBiomassKg() == null ? BigDecimal.ZERO : batch.getBiomassKg());
        record.setReason(reason);
        transferRecordMapper.insert(record);
        batch.setPondId(toPondId);
        batchMapper.updateById(batch);
        // 隔离调度/解除也是整批换池：成本账随批次走（同事务），事件口径与台账换池一致。
        eventPublisher.publishEvent(new BatchTransferredEvent(
                record.getId(), batch.getId(), batch.getId(), batch.getBatchNo(),
                fromPondId, toPondId, date,
                quantity, 0,
                record.getBiomassKg(), BigDecimal.ZERO, false,
                "TRANSFER-" + record.getId(), "ISOLATION"));
    }

    private DiseaseIsolation copyOf(DiseaseIsolation source) {
        DiseaseIsolation copy = new DiseaseIsolation();
        copy.setId(source.getId());
        copy.setIsolationNo(source.getIsolationNo());
        copy.setObservationId(source.getObservationId());
        copy.setBatchId(source.getBatchId());
        copy.setFromPondId(source.getFromPondId());
        copy.setToPondId(source.getToPondId());
        copy.setQuantity(source.getQuantity());
        copy.setStatus(source.getStatus());
        copy.setIsolatedBy(source.getIsolatedBy());
        copy.setIsolatedTime(source.getIsolatedTime());
        copy.setVetName(source.getVetName());
        copy.setVetReviewRemark(source.getVetReviewRemark());
        copy.setReleasedTime(source.getReleasedTime());
        copy.setRequestId(source.getRequestId());
        copy.setReleaseRequestId(source.getReleaseRequestId());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
