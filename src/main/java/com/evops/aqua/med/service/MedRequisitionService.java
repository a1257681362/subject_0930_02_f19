package com.evops.aqua.med.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DiseaseObservation;
import com.evops.aqua.disease.mapper.DiseaseObservationMapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.med.dto.RequisitionActionRequest;
import com.evops.aqua.med.dto.RequisitionCreateRequest;
import com.evops.aqua.med.dto.RequisitionItemRequest;
import com.evops.aqua.med.dto.RequisitionResubmitRequest;
import com.evops.aqua.med.dto.RequisitionReviewRequest;
import com.evops.aqua.med.entity.MedBatch;
import com.evops.aqua.med.entity.MedChangeEvidence;
import com.evops.aqua.med.entity.MedDrug;
import com.evops.aqua.med.entity.MedRequisition;
import com.evops.aqua.med.entity.MedRequisitionItem;
import com.evops.aqua.med.entity.MedWithdrawalLock;
import com.evops.aqua.med.mapper.MedRequisitionItemMapper;
import com.evops.aqua.med.mapper.MedRequisitionMapper;
import com.evops.aqua.med.mapper.MedWithdrawalLockMapper;
import com.evops.aqua.service.PondService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用药领用单：关联池塘、病情（疫病观察单）与药品批号。
 * 创建时按剂量×水体（面积×水深）估算用量并冻结剂量/休药期快照；
 * 提交后逐明细按"本明细冻结快照"计算休药期并锁定预计出池日（多药叠加取最晚结束日）；
 * 库存扣减与领用审核同一事务；撤回只能撤销未审核单，撤回后可重提形成版本链。
 *
 * 上下文隔离（修"较早批次休药期套到另一批次、出池日提前"）：
 * 1. 休药结束日只取自明细行冻结的 withdrawalDays 与本行 medBatchId，
 *    绝不到药品档案/其他领用单/其他池塘现取；历史明细永不回改；
 * 2. 同一池塘的提交/审核/驳回/撤回/重提经 {@link MedPondLockGuard} 按池串行，
 *    休药锁定读-改-写不会被并发事务覆盖；不同池塘键不同，跨池互不阻塞；
 * 3. 批号扣减为条件更新，跨池并发审核同批号不会超卖；
 * 4. 重提是"另立新版本"：旧撤回单与历史审核结论原样保留，绝不静默重算。
 */
@Service
public class MedRequisitionService {

    /** 角色视图：兽医看处方明细，仓管只看库存字段。 */
    public static final String ROLE_VET = "VET";

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final MedRequisitionMapper requisitionMapper;
    private final MedRequisitionItemMapper itemMapper;
    private final MedWithdrawalLockMapper lockMapper;
    private final MedDrugService drugService;
    private final MedBatchService batchService;
    private final PondService pondService;
    private final DiseaseObservationMapper observationMapper;
    private final MedEvidenceService evidenceService;
    private final MedPondLockGuard pondLockGuard;

    public MedRequisitionService(MedRequisitionMapper requisitionMapper,
                                 MedRequisitionItemMapper itemMapper,
                                 MedWithdrawalLockMapper lockMapper,
                                 MedDrugService drugService,
                                 MedBatchService batchService,
                                 PondService pondService,
                                 DiseaseObservationMapper observationMapper,
                                 MedEvidenceService evidenceService,
                                 MedPondLockGuard pondLockGuard) {
        this.requisitionMapper = requisitionMapper;
        this.itemMapper = itemMapper;
        this.lockMapper = lockMapper;
        this.drugService = drugService;
        this.batchService = batchService;
        this.pondService = pondService;
        this.observationMapper = observationMapper;
        this.evidenceService = evidenceService;
        this.pondLockGuard = pondLockGuard;
    }

    /**
     * 创建领用草稿：估算用量 = 剂量 × 水体；剂量/休药期冻结快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public MedRequisition create(RequisitionCreateRequest request) {
        MedRequisition duplicated = requisitionMapper.selectOne(new QueryWrapper<MedRequisition>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);
        validateObservation(pond.getId(), request.getObservationId());
        BigDecimal waterVolume = waterVolumeOf(pond);

        MedRequisition requisition = new MedRequisition();
        requisition.setReqNo(nextReqNo(pond.getId(), request.getUseDate()));
        requisition.setPondId(pond.getId());
        requisition.setObservationId(request.getObservationId());
        requisition.setDiseaseDesc(request.getDiseaseDesc());
        requisition.setUseDate(request.getUseDate());
        requisition.setWaterVolumeM3(waterVolume);
        requisition.setStatus(MedRequisition.STATUS_DRAFT);
        requisition.setApplicant(request.getApplicant());
        requisition.setRequestId(request.getRequestId());
        requisition.setRemark(request.getRemark());
        requisition.setRevNo(1);
        requisition.setOriginReqNo(requisition.getReqNo());
        requisitionMapper.insert(requisition);

        List<MedRequisitionItem> items = new ArrayList<>();
        for (RequisitionItemRequest line : request.getItems()) {
            items.add(buildItem(requisition, line));
        }
        evidenceService.record(MedChangeEvidence.ENTITY_REQUISITION, requisition.getId(),
                MedChangeEvidence.ACTION_REQ_CREATE, request.getRequestId(), request.getApplicant(),
                null, requisition, "创建领用草稿：" + requisition.getReqNo()
                        + "（v" + requisition.getRevNo() + "），明细" + items.size() + "行");
        return requisition;
    }

    /**
     * 提交：草稿转待审核，逐明细按本行冻结快照计算休药结束日并重算池塘出池锁定。
     * 同池并发经池塘互斥键串行，跨池互不阻塞。
     */
    @Transactional(rollbackFor = Exception.class)
    public MedRequisition submit(Long id, RequisitionActionRequest request) {
        MedRequisition requisition = getOrThrow(id);
        if (MedRequisition.STATUS_PENDING.equals(requisition.getStatus())
                || alreadyDone(MedChangeEvidence.ACTION_REQ_SUBMIT, id, request.getRequestId())) {
            return requisition;
        }
        if (!MedRequisition.STATUS_DRAFT.equals(requisition.getStatus())) {
            throw new IllegalArgumentException("仅草稿状态可提交，当前状态：" + requisition.getStatus());
        }
        pondLockGuard.lock(requisition.getPondId());
        requisition = getOrThrow(id);
        if (!MedRequisition.STATUS_DRAFT.equals(requisition.getStatus())) {
            throw new IllegalArgumentException("仅草稿状态可提交，当前状态：" + requisition.getStatus());
        }
        MedRequisition before = copyOf(requisition);
        applyWithdrawalEndDates(id, requisition.getUseDate());
        requisition.setStatus(MedRequisition.STATUS_PENDING);
        requisitionMapper.updateById(requisition);
        recomputeLock(requisition.getPondId(), request.getOperator(), request.getRequestId());
        evidenceService.record(MedChangeEvidence.ENTITY_REQUISITION, id,
                MedChangeEvidence.ACTION_REQ_SUBMIT, request.getRequestId(), request.getOperator(),
                before, requisition, "提交领用单，逐明细按冻结快照计算休药期并锁定预计出池日");
        return requisition;
    }

    /**
     * 审核通过：库存扣减与审核同一事务——任一明细库存不足则整体回滚，
     * 领用单停留待审核，失败原因独立事务留证。
     */
    @Transactional(rollbackFor = Exception.class)
    public MedRequisition approve(Long id, RequisitionReviewRequest request) {
        MedRequisition requisition = getOrThrow(id);
        if (MedRequisition.STATUS_APPROVED.equals(requisition.getStatus())
                || alreadyDone(MedChangeEvidence.ACTION_REQ_APPROVE, id, request.getRequestId())) {
            return requisition;
        }
        pondLockGuard.lock(requisition.getPondId());
        requisition = getOrThrow(id);
        if (MedRequisition.STATUS_APPROVED.equals(requisition.getStatus())) {
            return requisition;
        }
        if (!MedRequisition.STATUS_PENDING.equals(requisition.getStatus())) {
            throw new IllegalArgumentException("仅待审核单可审核通过，当前状态：" + requisition.getStatus());
        }
        List<MedRequisitionItem> items = itemsOf(id);
        List<String> conflicts = new ArrayList<>();
        Map<Long, MedBatch> batches = new HashMap<>();
        for (MedRequisitionItem item : items) {
            MedBatch batch = batchService.getOrThrow(item.getMedBatchId());
            batches.put(item.getId(), batch);
            if (batch.availableQuantity().compareTo(item.getEstimatedQty()) < 0) {
                conflicts.add("批号" + batch.getBatchNo() + "可用库存不足：可用"
                        + batch.availableQuantity() + "，需扣减" + item.getEstimatedQty());
            }
        }
        if (!conflicts.isEmpty()) {
            String detail = "审核失败（库存扣减与领用审核需一致，整体停留待审核）：" + String.join("；", conflicts);
            evidenceService.recordStandalone(MedChangeEvidence.ENTITY_REQUISITION, id,
                    MedChangeEvidence.ACTION_REQ_APPROVE_FAILED, request.getRequestId(),
                    request.getApprovedBy(), requisition, null, detail);
            throw new IllegalArgumentException(detail);
        }
        MedRequisition before = copyOf(requisition);
        try {
            for (MedRequisitionItem item : items) {
                batchService.deduct(batches.get(item.getId()), item.getEstimatedQty(),
                        request.getApprovedBy(), request.getRequestId(),
                        "领用审核通过扣减：领用单" + requisition.getReqNo());
            }
        } catch (IllegalArgumentException insufficient) {
            // 并发窗口下条件更新兜底拦截（如跨池同批号并发审核）：整体回滚，失败独立事务留证
            evidenceService.recordStandalone(MedChangeEvidence.ENTITY_REQUISITION, id,
                    MedChangeEvidence.ACTION_REQ_APPROVE_FAILED, request.getRequestId(),
                    request.getApprovedBy(), requisition, null,
                    "审核失败（库存扣减与领用审核需一致，整体停留待审核）：" + insufficient.getMessage());
            throw insufficient;
        }
        requisition.setStatus(MedRequisition.STATUS_APPROVED);
        requisition.setApprovedBy(request.getApprovedBy());
        requisition.setApproveRemark(request.getApproveRemark());
        requisition.setApprovedTime(LocalDateTime.now());
        requisitionMapper.updateById(requisition);
        evidenceService.record(MedChangeEvidence.ENTITY_REQUISITION, id,
                MedChangeEvidence.ACTION_REQ_APPROVE, request.getRequestId(), request.getApprovedBy(),
                before, requisition, "审核通过，库存同步扣减");
        return requisition;
    }

    /**
     * 审核驳回：退回并释放该单对池塘的休药锁定。
     */
    @Transactional(rollbackFor = Exception.class)
    public MedRequisition reject(Long id, RequisitionReviewRequest request) {
        MedRequisition requisition = getOrThrow(id);
        if (MedRequisition.STATUS_REJECTED.equals(requisition.getStatus())
                || alreadyDone(MedChangeEvidence.ACTION_REQ_REJECT, id, request.getRequestId())) {
            return requisition;
        }
        pondLockGuard.lock(requisition.getPondId());
        requisition = getOrThrow(id);
        if (MedRequisition.STATUS_REJECTED.equals(requisition.getStatus())) {
            return requisition;
        }
        if (!MedRequisition.STATUS_PENDING.equals(requisition.getStatus())) {
            throw new IllegalArgumentException("仅待审核单可驳回，当前状态：" + requisition.getStatus());
        }
        MedRequisition before = copyOf(requisition);
        requisition.setStatus(MedRequisition.STATUS_REJECTED);
        requisition.setApprovedBy(request.getApprovedBy());
        requisition.setApproveRemark(request.getApproveRemark());
        requisition.setApprovedTime(LocalDateTime.now());
        requisitionMapper.updateById(requisition);
        recomputeLock(requisition.getPondId(), request.getApprovedBy(), request.getRequestId());
        evidenceService.record(MedChangeEvidence.ENTITY_REQUISITION, id,
                MedChangeEvidence.ACTION_REQ_REJECT, request.getRequestId(), request.getApprovedBy(),
                before, requisition, "审核驳回：" + request.getApproveRemark());
        return requisition;
    }

    /**
     * 撤回：只能撤销未审核单（草稿/待审核）；已审核单一律拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public MedRequisition withdraw(Long id, RequisitionActionRequest request) {
        MedRequisition requisition = getOrThrow(id);
        if (MedRequisition.STATUS_WITHDRAWN.equals(requisition.getStatus())
                || alreadyDone(MedChangeEvidence.ACTION_REQ_WITHDRAW, id, request.getRequestId())) {
            return requisition;
        }
        pondLockGuard.lock(requisition.getPondId());
        requisition = getOrThrow(id);
        if (MedRequisition.STATUS_WITHDRAWN.equals(requisition.getStatus())) {
            return requisition;
        }
        boolean unreviewed = MedRequisition.STATUS_DRAFT.equals(requisition.getStatus())
                || MedRequisition.STATUS_PENDING.equals(requisition.getStatus());
        if (!unreviewed) {
            throw new IllegalArgumentException("已审核单不可撤回（当前状态：" + requisition.getStatus()
                    + "），撤回只能撤销未审核单");
        }
        MedRequisition before = copyOf(requisition);
        requisition.setStatus(MedRequisition.STATUS_WITHDRAWN);
        requisition.setWithdrawBy(request.getOperator());
        requisition.setWithdrawTime(LocalDateTime.now());
        requisitionMapper.updateById(requisition);
        recomputeLock(requisition.getPondId(), request.getOperator(), request.getRequestId());
        evidenceService.record(MedChangeEvidence.ENTITY_REQUISITION, id,
                MedChangeEvidence.ACTION_REQ_WITHDRAW, request.getRequestId(), request.getOperator(),
                before, requisition, "撤回未审核领用单，释放休药锁定");
        return requisition;
    }

    /**
     * 撤回后重提：仅已撤回单可重提。另立新版本领用单（新 id、新编号、新版本号），
     * 旧撤回单与其明细/证据/历史审核结论原样保留，绝不回改、绝不静默重算。
     * 明细缺省沿用上一版（用药/批号/剂量），也可在请求中重新指定；
     * 剂量/休药期按重提当时重新冻结，批次关联版本随版本号递增。
     * 重提单直接进入待审核，按新版本快照计算休药期并重算池塘锁定。
     */
    @Transactional(rollbackFor = Exception.class)
    public MedRequisition resubmit(Long id, RequisitionResubmitRequest request) {
        MedRequisition duplicated = requisitionMapper.selectOne(new QueryWrapper<MedRequisition>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        MedRequisition previous = getOrThrow(id);
        if (!MedRequisition.STATUS_WITHDRAWN.equals(previous.getStatus())) {
            throw new IllegalArgumentException("仅已撤回单可重提，当前状态：" + previous.getStatus()
                    + "（编号" + previous.getReqNo() + "）");
        }
        pondLockGuard.lock(previous.getPondId());
        previous = getOrThrow(id);
        if (!MedRequisition.STATUS_WITHDRAWN.equals(previous.getStatus())) {
            throw new IllegalArgumentException("仅已撤回单可重提，当前状态：" + previous.getStatus());
        }
        Long successors = requisitionMapper.selectCount(new QueryWrapper<MedRequisition>()
                .eq("resubmit_of_id", id));
        if (successors != null && successors > 0) {
            // 持锁后判定：并发重提只有一个请求能建出后续版本，其余在此被拒，版本链不分叉
            throw new IllegalArgumentException("领用单" + previous.getReqNo()
                    + "已重提形成新版本，不能重复重提");
        }

        Pond pond = pondService.getOrThrow(previous.getPondId());
        LocalDate useDate = request.getUseDate() != null ? request.getUseDate() : previous.getUseDate();
        String diseaseDesc = request.getDiseaseDesc() != null && !request.getDiseaseDesc().trim().isEmpty()
                ? request.getDiseaseDesc() : previous.getDiseaseDesc();
        validateObservation(pond.getId(), previous.getObservationId());

        int newRev = (previous.getRevNo() == null ? 1 : previous.getRevNo()) + 1;
        String originNo = previous.getOriginReqNo() != null ? previous.getOriginReqNo() : baseReqNo(previous.getReqNo());

        MedRequisition requisition = new MedRequisition();
        requisition.setReqNo(originNo + "-R" + newRev);
        requisition.setPondId(pond.getId());
        requisition.setObservationId(previous.getObservationId());
        requisition.setDiseaseDesc(diseaseDesc);
        requisition.setUseDate(useDate);
        requisition.setWaterVolumeM3(previous.getWaterVolumeM3());
        requisition.setStatus(MedRequisition.STATUS_PENDING);
        requisition.setApplicant(previous.getApplicant());
        requisition.setRequestId(request.getRequestId());
        requisition.setRemark(request.getRemark());
        requisition.setRevNo(newRev);
        requisition.setOriginReqNo(originNo);
        requisition.setResubmitOfId(previous.getId());
        requisition.setResubmitTime(LocalDateTime.now());
        requisitionMapper.insert(requisition);

        List<RequisitionItemRequest> lines = request.getItems();
        if (lines == null || lines.isEmpty()) {
            lines = new ArrayList<>();
            for (MedRequisitionItem old : itemsOf(id)) {
                RequisitionItemRequest copied = new RequisitionItemRequest();
                copied.setDrugId(old.getDrugId());
                copied.setMedBatchId(old.getMedBatchId());
                copied.setDosagePerM3(old.getDosagePerM3());
                lines.add(copied);
            }
        }
        List<MedRequisitionItem> items = new ArrayList<>();
        for (RequisitionItemRequest line : lines) {
            items.add(buildItem(requisition, line));
        }
        applyWithdrawalEndDates(requisition.getId(), requisition.getUseDate());
        recomputeLock(pond.getId(), request.getOperator(), request.getRequestId());
        evidenceService.record(MedChangeEvidence.ENTITY_REQUISITION, requisition.getId(),
                MedChangeEvidence.ACTION_REQ_RESUBMIT, request.getRequestId(), request.getOperator(),
                previous, requisition, "撤回后重提：由" + previous.getReqNo() + "（v" + previous.getRevNo()
                        + "，已撤回保留）重提为" + requisition.getReqNo() + "（v" + newRev
                        + "），批次关联版本v" + newRev + "，明细" + items.size() + "行");
        return requisition;
    }

    /**
     * 角色视图：VET（兽医）看处方明细（病情/剂量/休药期）；
     * 其他角色（含仓管 KEEPER）只看库存字段（批号/数量/效期），看不到处方信息。
     */
    public Map<String, Object> view(Long id, String role) {
        MedRequisition requisition = getOrThrow(id);
        List<MedRequisitionItem> items = itemsOf(id);
        boolean vet = ROLE_VET.equalsIgnoreCase(role == null ? "" : role.trim());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reqNo", requisition.getReqNo());
        view.put("status", requisition.getStatus());
        view.put("useDate", requisition.getUseDate());
        view.put("revNo", requisition.getRevNo());
        view.put("originReqNo", requisition.getOriginReqNo());
        view.put("resubmitOfId", requisition.getResubmitOfId());
        view.put("withdrawTime", requisition.getWithdrawTime());
        view.put("resubmitTime", requisition.getResubmitTime());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (MedRequisitionItem item : items) {
            MedDrug drug = drugService.getOrThrow(item.getDrugId());
            MedBatch batch = batchService.getOrThrow(item.getMedBatchId());
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("drugName", drug.getDrugName());
            line.put("batchNo", batch.getBatchNo());
            line.put("batchNoSnapshot", item.getBatchNoSnapshot());
            line.put("linkVersion", item.getLinkVersion());
            line.put("estimatedQty", item.getEstimatedQty());
            line.put("unit", drug.getUnit());
            line.put("expiryDate", batch.getExpiryDate());
            if (vet) {
                line.put("dosagePerM3", item.getDosagePerM3());
                line.put("withdrawalDays", item.getWithdrawalDays());
                line.put("withdrawalEndDate", item.getWithdrawalEndDate());
            }
            lines.add(line);
        }
        view.put("items", lines);
        if (vet) {
            view.put("pondId", requisition.getPondId());
            view.put("observationId", requisition.getObservationId());
            view.put("diseaseDesc", requisition.getDiseaseDesc());
            view.put("waterVolumeM3", requisition.getWaterVolumeM3());
            view.put("applicant", requisition.getApplicant());
            view.put("approvedBy", requisition.getApprovedBy());
            view.put("approveRemark", requisition.getApproveRemark());
        }
        return view;
    }

    public MedRequisition getOrThrow(Long id) {
        MedRequisition requisition = requisitionMapper.selectById(id);
        if (requisition == null) {
            throw new IllegalArgumentException("领用单不存在，id=" + id);
        }
        return requisition;
    }

    public List<MedRequisition> list(Long pondId, String status) {
        QueryWrapper<MedRequisition> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return requisitionMapper.selectList(wrapper);
    }

    public List<MedRequisitionItem> itemsOf(Long requisitionId) {
        return itemMapper.selectList(new QueryWrapper<MedRequisitionItem>()
                .eq("requisition_id", requisitionId).orderByAsc("id"));
    }

    /**
     * 构建一行明细：校验药品/批号归属、在用与效期，按本行剂量×本行水体估算用量，
     * 剂量与休药期取自此刻药品档案并冻结为快照；批号与关联版本一并固化。
     */
    private MedRequisitionItem buildItem(MedRequisition requisition, RequisitionItemRequest line) {
        MedDrug drug = drugService.getOrThrow(line.getDrugId());
        drugService.requireActive(drug);
        MedBatch batch = batchService.getOrThrow(line.getMedBatchId());
        if (!batch.getDrugId().equals(drug.getId())) {
            throw new IllegalArgumentException("批号" + batch.getBatchNo()
                    + "不属于药品" + drug.getDrugName());
        }
        if (!MedBatch.STATUS_IN_STOCK.equals(batch.getStatus())) {
            throw new IllegalArgumentException("批号" + batch.getBatchNo() + "已用尽，不能领用");
        }
        if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(requisition.getUseDate())) {
            throw new IllegalArgumentException("批号" + batch.getBatchNo() + "在用药日前已过期（有效期至"
                    + batch.getExpiryDate() + "），不能领用");
        }
        BigDecimal dosage = line.getDosagePerM3() != null ? line.getDosagePerM3() : drug.getDosagePerM3();
        MedRequisitionItem item = new MedRequisitionItem();
        item.setRequisitionId(requisition.getId());
        item.setDrugId(drug.getId());
        item.setMedBatchId(batch.getId());
        item.setDosagePerM3(dosage);
        item.setEstimatedQty(dosage.multiply(requisition.getWaterVolumeM3()).setScale(2, RoundingMode.HALF_UP));
        item.setWithdrawalDays(drug.getWithdrawalDays());
        item.setLinkVersion(requisition.getRevNo() == null ? 1 : requisition.getRevNo());
        item.setBatchNoSnapshot(batch.getBatchNo());
        itemMapper.insert(item);
        return item;
    }

    /**
     * 逐明细计算休药结束日：用药日 + 本明细行冻结的休药天数。
     * 只依赖本行快照，不读药品档案、不读其他池塘/其他领用单——
     * 这是"同一药品被两个池塘领用后批次休药期互相串用"的直接防线。
     */
    private void applyWithdrawalEndDates(Long requisitionId, LocalDate useDate) {
        for (MedRequisitionItem item : itemsOf(requisitionId)) {
            if (item.getWithdrawalEndDate() == null) {
                item.setWithdrawalEndDate(useDate.plusDays(item.getWithdrawalDays()));
                itemMapper.updateById(item);
            }
        }
    }

    private void validateObservation(Long pondId, Long observationId) {
        if (observationId == null) {
            return;
        }
        DiseaseObservation observation = observationMapper.selectById(observationId);
        if (observation == null) {
            throw new IllegalArgumentException("病情观察单不存在，id=" + observationId);
        }
        if (!observation.getPondId().equals(pondId)) {
            throw new IllegalArgumentException("病情观察单" + observation.getObsNo()
                    + "属于池塘id=" + observation.getPondId() + "，与领用池塘id=" + pondId + "不一致");
        }
    }

    /**
     * 重算池塘休药锁定（调用方须已持有该池互斥锁）：取该池在途（待审核/已审核）
     * 领用单各明细休药结束日的最晚值——多药叠加取最晚；无在途单则释放锁定。锁定变化写证据。
     */
    private void recomputeLock(Long pondId, String operator, String requestId) {
        List<MedRequisition> active = requisitionMapper.selectList(new QueryWrapper<MedRequisition>()
                .eq("pond_id", pondId)
                .in("status", MedRequisition.STATUS_PENDING, MedRequisition.STATUS_APPROVED));
        LocalDate latest = null;
        String sourceReqNo = null;
        for (MedRequisition req : active) {
            for (MedRequisitionItem item : itemsOf(req.getId())) {
                if (item.getWithdrawalEndDate() != null
                        && (latest == null || item.getWithdrawalEndDate().isAfter(latest))) {
                    latest = item.getWithdrawalEndDate();
                    sourceReqNo = req.getReqNo();
                }
            }
        }
        MedWithdrawalLock lock = lockMapper.selectOne(new QueryWrapper<MedWithdrawalLock>()
                .eq("pond_id", pondId).last("limit 1"));
        if (latest == null) {
            if (lock != null) {
                lockMapper.deleteById(lock.getId());
                evidenceService.record(MedChangeEvidence.ENTITY_WITHDRAWAL_LOCK, lock.getId(),
                        MedChangeEvidence.ACTION_LOCK_RELEASE, requestId, operator, lock, null,
                        "池塘id=" + pondId + "无在途领用单，释放休药锁定");
            }
            return;
        }
        String detail = "最晚休药结束日来自领用单" + sourceReqNo + "（多药叠加取最晚）";
        if (lock == null) {
            lock = new MedWithdrawalLock();
            lock.setPondId(pondId);
            lock.setLockedUntil(latest);
            lock.setDetail(detail);
            lockMapper.insert(lock);
            evidenceService.record(MedChangeEvidence.ENTITY_WITHDRAWAL_LOCK, lock.getId(),
                    MedChangeEvidence.ACTION_LOCK_UPDATE, requestId, operator, null, lock,
                    "池塘id=" + pondId + "锁定预计出池日不早于" + latest);
        } else if (!latest.equals(lock.getLockedUntil())) {
            MedWithdrawalLock before = new MedWithdrawalLock();
            before.setPondId(lock.getPondId());
            before.setLockedUntil(lock.getLockedUntil());
            before.setDetail(lock.getDetail());
            lock.setLockedUntil(latest);
            lock.setDetail(detail);
            lockMapper.updateById(lock);
            evidenceService.record(MedChangeEvidence.ENTITY_WITHDRAWAL_LOCK, lock.getId(),
                    MedChangeEvidence.ACTION_LOCK_UPDATE, requestId, operator, before, lock,
                    "池塘id=" + pondId + "休药锁定调整为" + latest);
        }
    }

    private boolean alreadyDone(String action, Long id, String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            return false;
        }
        return evidenceService.list(MedChangeEvidence.ENTITY_REQUISITION, id, requestId)
                .stream().anyMatch(e -> action.equals(e.getAction()));
    }

    private BigDecimal waterVolumeOf(Pond pond) {
        if (pond.getWaterAreaM2() == null || pond.getWaterDepthM() == null) {
            throw new IllegalArgumentException("池塘" + pond.getPondCode()
                    + "缺少水面面积或水深档案，无法按水体估算用药量，请先完善池塘档案");
        }
        return pond.getWaterAreaM2().multiply(pond.getWaterDepthM()).setScale(2, RoundingMode.HALF_UP);
    }

    private String nextReqNo(Long pondId, LocalDate useDate) {
        Long count = requisitionMapper.selectCount(new QueryWrapper<MedRequisition>()
                .eq("pond_id", pondId).eq("use_date", useDate));
        return "MR-" + pondId + "-" + useDate.format(DAY) + "-" + (count + 1);
    }

    /** 去掉重提后缀（-R2 等），取版本链基础编号。 */
    private String baseReqNo(String reqNo) {
        int cut = reqNo.indexOf("-R");
        return cut > 0 ? reqNo.substring(0, cut) : reqNo;
    }

    private MedRequisition copyOf(MedRequisition requisition) {
        MedRequisition copy = new MedRequisition();
        org.springframework.beans.BeanUtils.copyProperties(requisition, copy);
        return copy;
    }
}
