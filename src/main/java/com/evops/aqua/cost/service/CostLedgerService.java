package com.evops.aqua.cost.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.cost.dto.AdjustmentCreateRequest;
import com.evops.aqua.cost.dto.AdjustmentReviewRequest;
import com.evops.aqua.cost.dto.CostEntryCreateRequest;
import com.evops.aqua.cost.entity.CostAdjustment;
import com.evops.aqua.cost.entity.CostBalance;
import com.evops.aqua.cost.entity.CostCarryover;
import com.evops.aqua.cost.entity.CostCarryoverLine;
import com.evops.aqua.cost.entity.CostChangeEvidence;
import com.evops.aqua.cost.entity.CostEntry;
import com.evops.aqua.cost.entity.CostTransfer;
import com.evops.aqua.cost.entity.CostTransferLine;
import com.evops.aqua.cost.entity.CostVoucher;
import com.evops.aqua.cost.mapper.CostAdjustmentMapper;
import com.evops.aqua.cost.mapper.CostBalanceMapper;
import com.evops.aqua.cost.mapper.CostCarryoverLineMapper;
import com.evops.aqua.cost.mapper.CostCarryoverMapper;
import com.evops.aqua.cost.mapper.CostEntryMapper;
import com.evops.aqua.cost.mapper.CostTransferLineMapper;
import com.evops.aqua.cost.mapper.CostTransferMapper;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.event.BatchHarvestedEvent;
import com.evops.aqua.event.BatchTransferredEvent;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 养殖成本核心台账：四类费用按池批归集 → 转池按数量与生物量权重分配未结成本
 * → 出池全部结转形成不可变快照；差异调整待审核后才入账。
 * 已结转（已出池）批次拒绝任何新费用凭条与差异单，不能被新单据改写。
 */
@Service
public class CostLedgerService {

    public static final String ROLE_FINANCE = "FINANCE";
    public static final String ROLE_PRODUCTION = "PRODUCTION";

    public static final List<String> COST_TYPES =
            Arrays.asList(CostEntry.TYPE_FEED, CostEntry.TYPE_LABOR, CostEntry.TYPE_MED, CostEntry.TYPE_ENERGY);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final BigDecimal HALF = new BigDecimal("0.5");

    private final CostEntryMapper entryMapper;
    private final CostBalanceMapper balanceMapper;
    private final CostTransferMapper transferMapper;
    private final CostTransferLineMapper transferLineMapper;
    private final CostCarryoverMapper carryoverMapper;
    private final CostCarryoverLineMapper carryoverLineMapper;
    private final CostAdjustmentMapper adjustmentMapper;
    private final CostVoucherService voucherService;
    private final CostEvidenceService evidenceService;
    private final SeedlingBatchService batchService;
    private final PondService pondService;
    private final HarvestRecordMapper harvestRecordMapper;
    private final ObjectMapper objectMapper;

    public CostLedgerService(CostEntryMapper entryMapper,
                             CostBalanceMapper balanceMapper,
                             CostTransferMapper transferMapper,
                             CostTransferLineMapper transferLineMapper,
                             CostCarryoverMapper carryoverMapper,
                             CostCarryoverLineMapper carryoverLineMapper,
                             CostAdjustmentMapper adjustmentMapper,
                             CostVoucherService voucherService,
                             CostEvidenceService evidenceService,
                             SeedlingBatchService batchService,
                             PondService pondService,
                             HarvestRecordMapper harvestRecordMapper,
                             ObjectMapper objectMapper) {
        this.entryMapper = entryMapper;
        this.balanceMapper = balanceMapper;
        this.transferMapper = transferMapper;
        this.transferLineMapper = transferLineMapper;
        this.carryoverMapper = carryoverMapper;
        this.carryoverLineMapper = carryoverLineMapper;
        this.adjustmentMapper = adjustmentMapper;
        this.voucherService = voucherService;
        this.evidenceService = evidenceService;
        this.batchService = batchService;
        this.pondService = pondService;
        this.harvestRecordMapper = harvestRecordMapper;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 费用归集（人工补录，必须引用凭证）
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public CostEntry createEntry(CostEntryCreateRequest request) {
        CostEntry duplicated = entryMapper.selectOne(
                new QueryWrapper<CostEntry>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated;
        }
        requireCostType(request.getCostType());
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("费用金额必须大于0");
        }
        SeedlingBatch batch = batchService.getOrThrow(request.getBatchId());
        if (SeedlingBatch.STATUS_HARVESTED.equals(batch.getStatus())) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "已出池结转，成本账已封冻，不能再登记费用");
        }
        if (!batch.getPondId().equals(request.getPondId())) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "当前不在池塘id=" + request.getPondId() + "，不能按该池归集费用");
        }
        CostVoucher voucher = voucherService.requireActive(request.getVoucherNo());

        CostEntry entry = new CostEntry();
        entry.setEntryNo(nextEntryNo(batch.getId(), request.getOccurDate()));
        entry.setPondId(batch.getPondId());
        entry.setBatchId(batch.getId());
        entry.setCostType(request.getCostType());
        entry.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        entry.setOccurDate(request.getOccurDate());
        entry.setSource(CostEntry.SOURCE_MANUAL);
        entry.setVoucherId(voucher.getId());
        entry.setVoucherNo(voucher.getVoucherNo());
        entry.setBatchStatusAtEntry(batch.getStatus());
        entry.setRequestId(request.getRequestId());
        entry.setOperator(request.getOperator());
        entry.setRemark(request.getRemark());
        entryMapper.insert(entry);

        CostBalance balance = getOrCreateBalance(batch, request.getCostType());
        balance.setSettledAmount(nz(balance.getSettledAmount()).add(entry.getAmount()));
        syncStock(balance, batch);
        balanceMapper.updateById(balance);

        evidenceService.record(CostChangeEvidence.ENTITY_ENTRY, entry.getId(),
                CostChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getOperator(),
                null, entry, "人工补录归集费用：类型=" + request.getCostType()
                        + "，金额=" + entry.getAmount() + "，凭证=" + voucher.getVoucherNo()
                        + "，批次=" + batch.getBatchNo());
        return entry;
    }

    // ============================================================
    // 转池：按数量与生物量权重分配未结成本（同事务事件监听）
    // ============================================================

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onTransferred(BatchTransferredEvent event) {
        CostTransfer existed = transferMapper.selectOne(
                new QueryWrapper<CostTransfer>().eq("transfer_record_id", event.getTransferRecordId()));
        if (existed != null) {
            return; // 事件重放幂等：一条台账换池只做一次成本分配
        }

        SeedlingBatch source = batchService.getOrThrow(event.getSourceBatchId());
        SeedlingBatch receive = batchService.getOrThrow(event.getReceiveBatchId());

        CostTransfer transfer = new CostTransfer();
        transfer.setTransferNo("CT-" + event.getTransferRecordId());
        transfer.setTransferRecordId(event.getTransferRecordId());
        transfer.setFromPondId(event.getFromPondId());
        transfer.setToPondId(event.getToPondId());
        transfer.setSourceBatchId(source.getId());
        transfer.setMovedQuantity(event.getMovedQuantity());
        transfer.setMovedBiomassKg(event.getMovedBiomassKg());
        transfer.setBasis("QUANTITY_0.5_BIOMASS_0.5");
        transfer.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        transfer.setRequestId(event.getRequestId());
        transferMapper.insert(transfer);

        List<Map<String, Object>> allocationSnapshot = new ArrayList<>();
        BigDecimal totalAllocated = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        for (String costType : COST_TYPES) {
            CostBalance sourceBalance = balanceMapper.selectOne(new QueryWrapper<CostBalance>()
                    .eq("batch_id", source.getId()).eq("cost_type", costType));
            if (sourceBalance == null) {
                continue;
            }
            BigDecimal settled = nz(sourceBalance.getSettledAmount());

            if (!event.isSplit()) {
                // 整批转移：未结成本100%随批走，仅把余额账挪到目标池，不产生新凭条、不重算金额。
                CostTransferLine line = newTransferLine(transfer.getId(), source.getId(), source.getId(),
                        event.getToPondId(), costType, event.getMovedQuantity(),
                        event.getMovedBiomassKg(), BigDecimal.ONE, settled);
                transferLineMapper.insert(line);
                sourceBalance.setPondId(event.getToPondId());
                syncStock(sourceBalance, receive);
                balanceMapper.updateById(sourceBalance);
                totalAllocated = totalAllocated.add(settled);
                allocationSnapshot.add(lineSnapshot(line));
                continue;
            }

            // 分池：50% 数量权重 + 50% 生物量权重；转出前总生物量为 0 时退化为纯数量权重。
            int qtyBefore = event.getMovedQuantity() + event.getRemainQuantity();
            BigDecimal bioBefore = nz(event.getMovedBiomassKg()).add(nz(event.getRemainBiomassKg()));
            BigDecimal weightMoved;
            String basis;
            if (bioBefore.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal qPart = BigDecimal.valueOf(event.getMovedQuantity())
                        .divide(BigDecimal.valueOf(qtyBefore), 6, RoundingMode.HALF_UP);
                BigDecimal bPart = event.getMovedBiomassKg()
                        .divide(bioBefore, 6, RoundingMode.HALF_UP);
                weightMoved = qPart.multiply(HALF).add(bPart.multiply(HALF)).setScale(6, RoundingMode.HALF_UP);
                basis = "数量权重50%+生物量权重50%";
            } else {
                weightMoved = BigDecimal.valueOf(event.getMovedQuantity())
                        .divide(BigDecimal.valueOf(qtyBefore), 6, RoundingMode.HALF_UP);
                basis = "生物量为0，退化为数量权重100%";
            }
            if (weightMoved.compareTo(BigDecimal.ONE) > 0) {
                weightMoved = BigDecimal.ONE;
            }
            BigDecimal weightStay = BigDecimal.ONE.subtract(weightMoved);
            BigDecimal allocatedMoved = settled.multiply(weightMoved).setScale(2, RoundingMode.HALF_UP);
            BigDecimal allocatedStay = settled.subtract(allocatedMoved);

            CostTransferLine stayLine = newTransferLine(transfer.getId(), source.getId(), source.getId(),
                    event.getFromPondId(), costType, event.getRemainQuantity(),
                    event.getRemainBiomassKg(), weightStay, allocatedStay);
            transferLineMapper.insert(stayLine);
            CostTransferLine movedLine = newTransferLine(transfer.getId(), source.getId(), receive.getId(),
                    event.getToPondId(), costType, event.getMovedQuantity(),
                    event.getMovedBiomassKg(), weightMoved, allocatedMoved);
            transferLineMapper.insert(movedLine);

            // 源批次：留存余额 + 累计分出
            sourceBalance.setSettledAmount(allocatedStay);
            sourceBalance.setCarryoverOutAmount(nz(sourceBalance.getCarryoverOutAmount()).add(allocatedMoved));
            syncStock(sourceBalance, source);
            balanceMapper.updateById(sourceBalance);

            // 接收批次：分得余额 + 累计转入，凭条只追加可逐笔追溯
            CostBalance childBalance = getOrCreateBalance(receive, costType);
            childBalance.setSettledAmount(nz(childBalance.getSettledAmount()).add(allocatedMoved));
            childBalance.setCarryoverInAmount(nz(childBalance.getCarryoverInAmount()).add(allocatedMoved));
            syncStock(childBalance, receive);
            balanceMapper.updateById(childBalance);

            CostEntry carryIn = new CostEntry();
            carryIn.setEntryNo("CE-CO-" + event.getTransferRecordId() + "-" + costType);
            carryIn.setPondId(receive.getPondId());
            carryIn.setBatchId(receive.getId());
            carryIn.setCostType(costType);
            carryIn.setAmount(allocatedMoved);
            carryIn.setOccurDate(event.getTransferDate());
            carryIn.setSource(CostEntry.SOURCE_CARRYOVER);
            carryIn.setTransferId(transfer.getId());
            carryIn.setBatchStatusAtEntry(receive.getStatus());
            carryIn.setRequestId(event.getRequestId() + "-" + costType);
            carryIn.setOperator(event.getOperator());
            carryIn.setRemark("转池分配自批次" + source.getBatchNo() + "（" + basis + "）");
            entryMapper.insert(carryIn);

            totalAllocated = totalAllocated.add(allocatedMoved);
            allocationSnapshot.add(lineSnapshot(stayLine));
            allocationSnapshot.add(lineSnapshot(movedLine));
        }

        transfer.setBasis("QUANTITY_0.5_BIOMASS_0.5");
        transfer.setTotalAmount(totalAllocated);
        transfer.setAllocationJson(toJson(allocationSnapshot));
        transferMapper.updateById(transfer);

        evidenceService.record(CostChangeEvidence.ENTITY_TRANSFER, transfer.getId(),
                CostChangeEvidence.ACTION_ALLOCATE, event.getRequestId(), event.getOperator(),
                null, transfer,
                "转池成本分配：源批次=" + source.getBatchNo() + "，接收批次=" + receive.getBatchNo()
                        + (event.isSplit() ? "（分池）" : "（整批转移）")
                        + "，分出数量=" + event.getMovedQuantity() + "，分出生物量=" + event.getMovedBiomassKg()
                        + "，分配金额合计=" + totalAllocated);
    }

    // ============================================================
    // 出池：未结成本全部结转，形成不可变快照
    // ============================================================

    @EventListener
    @Transactional(rollbackFor = Exception.class)
    public void onHarvested(BatchHarvestedEvent event) {
        CostCarryover existed = carryoverMapper.selectOne(
                new QueryWrapper<CostCarryover>().eq("harvest_record_id", event.getHarvestRecordId()));
        if (existed != null) {
            return; // 事件重放幂等
        }
        SeedlingBatch batch = batchService.getOrThrow(event.getBatchId());
        Pond pond = pondService.getOrThrow(event.getPondId());
        HarvestRecord harvest = harvestRecordMapper.selectById(event.getHarvestRecordId());

        CostCarryover carryover = new CostCarryover();
        carryover.setCarryoverNo("CO-" + batch.getId());
        carryover.setBatchId(batch.getId());
        carryover.setPondId(pond.getId());
        carryover.setPondStatusAtCarryover(pond.getStatus());
        carryover.setHarvestRecordId(harvest.getId());
        carryover.setHarvestDate(harvest.getHarvestDate());
        carryover.setQuantity(harvest.getQuantity());
        carryover.setBiomassKg(nz(batch.getBiomassKg()));
        carryover.setRequestId(event.getRequestId());
        carryover.setTotalAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        carryoverMapper.insert(carryover);

        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<Map<String, Object>> snapshot = new ArrayList<>();
        for (String costType : COST_TYPES) {
            CostBalance balance = balanceMapper.selectOne(new QueryWrapper<CostBalance>()
                    .eq("batch_id", batch.getId()).eq("cost_type", costType));
            BigDecimal settled = balance == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                    : nz(balance.getSettledAmount());
            BigDecimal carryIn = balance == null ? BigDecimal.ZERO : nz(balance.getCarryoverInAmount());
            BigDecimal carryOut = balance == null ? BigDecimal.ZERO : nz(balance.getCarryoverOutAmount());
            Long entryCount = entryMapper.selectCount(new QueryWrapper<CostEntry>()
                    .eq("batch_id", batch.getId()).eq("cost_type", costType));

            CostCarryoverLine line = new CostCarryoverLine();
            line.setCarryoverId(carryover.getId());
            line.setCostType(costType);
            line.setSettledAmount(settled);
            line.setCarryoverInAmount(carryIn);
            line.setCarryoverOutAmount(carryOut);
            line.setEntryCount(entryCount == null ? 0 : entryCount.intValue());
            carryoverLineMapper.insert(line);

            if (balance != null && settled.compareTo(BigDecimal.ZERO) != 0) {
                balance.setSettledAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                balance.setCarryoverOutAmount(carryOut.add(settled));
                balanceMapper.updateById(balance);
            }
            total = total.add(settled);

            Map<String, Object> lineSnapshot = new LinkedHashMap<>();
            lineSnapshot.put("costType", costType);
            lineSnapshot.put("settledAmount", settled);
            lineSnapshot.put("carryoverInAmount", carryIn);
            lineSnapshot.put("carryoverOutAmount", carryOut);
            lineSnapshot.put("entryCount", line.getEntryCount());
            snapshot.add(lineSnapshot);
        }

        carryover.setTotalAmount(total);
        Map<String, Object> fullSnapshot = new LinkedHashMap<>();
        fullSnapshot.put("batchNo", batch.getBatchNo());
        fullSnapshot.put("pondId", pond.getId());
        fullSnapshot.put("pondCode", pond.getPondCode());
        fullSnapshot.put("pondStatus", pond.getStatus());
        fullSnapshot.put("harvestDate", harvest.getHarvestDate().toString());
        fullSnapshot.put("quantity", harvest.getQuantity());
        fullSnapshot.put("biomassKg", carryover.getBiomassKg());
        fullSnapshot.put("lines", snapshot);
        carryover.setSnapshotJson(toJson(fullSnapshot));
        carryoverMapper.updateById(carryover);

        evidenceService.record(CostChangeEvidence.ENTITY_CARRYOVER, carryover.getId(),
                CostChangeEvidence.ACTION_CARRYOVER, event.getRequestId(), event.getOperator(),
                null, carryover,
                "出池结转快照：批次=" + batch.getBatchNo() + "，结转金额合计=" + total
                        + "，结转后余额清零且封冻");
    }

    // ============================================================
    // 差异调整：必须待审核，通过后才入账
    // ============================================================

    @Transactional(rollbackFor = Exception.class)
    public CostAdjustment createAdjustment(AdjustmentCreateRequest request) {
        CostAdjustment duplicated = adjustmentMapper.selectOne(
                new QueryWrapper<CostAdjustment>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated;
        }
        requireCostType(request.getCostType());
        if (!CostAdjustment.DIRECTION_ADD.equals(request.getDirection())
                && !CostAdjustment.DIRECTION_SUB.equals(request.getDirection())) {
            throw new IllegalArgumentException("调整方向只能是 ADD 或 SUB");
        }
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("差异金额必须大于0");
        }
        SeedlingBatch batch = batchService.getOrThrow(request.getBatchId());
        if (SeedlingBatch.STATUS_HARVESTED.equals(batch.getStatus())) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "已出池结转，不能再登记差异调整（已结转批次不能被改写）");
        }
        CostVoucher voucher = voucherService.requireActive(request.getVoucherNo());

        CostAdjustment adjustment = new CostAdjustment();
        adjustment.setAdjNo(nextAdjNo(batch.getId()));
        adjustment.setBatchId(batch.getId());
        adjustment.setPondId(batch.getPondId());
        adjustment.setCostType(request.getCostType());
        adjustment.setDirection(request.getDirection());
        adjustment.setAmount(request.getAmount().setScale(2, RoundingMode.HALF_UP));
        adjustment.setOccurDate(request.getOccurDate());
        adjustment.setVoucherId(voucher.getId());
        adjustment.setVoucherNo(voucher.getVoucherNo());
        adjustment.setReason(request.getReason());
        adjustment.setStatus(CostAdjustment.STATUS_PENDING);
        adjustment.setApplicant(request.getApplicant());
        adjustment.setRequestId(request.getRequestId());
        adjustmentMapper.insert(adjustment);

        evidenceService.record(CostChangeEvidence.ENTITY_ADJUSTMENT, adjustment.getId(),
                CostChangeEvidence.ACTION_CREATE, request.getRequestId(), request.getApplicant(),
                null, adjustment,
                "差异调整单建单（待审核）：批次=" + batch.getBatchNo() + "，类型=" + request.getCostType()
                        + "，方向=" + request.getDirection() + "，金额=" + adjustment.getAmount());
        return adjustment;
    }

    @Transactional(rollbackFor = Exception.class)
    public CostAdjustment reviewAdjustment(Long id, boolean approve, AdjustmentReviewRequest request) {
        CostAdjustment adjustment = getAdjustmentOrThrow(id);
        if (!CostAdjustment.STATUS_PENDING.equals(adjustment.getStatus())) {
            return adjustment; // 重复审核幂等：不多入一笔账
        }
        SeedlingBatch batch = batchService.getOrThrow(adjustment.getBatchId());
        if (SeedlingBatch.STATUS_HARVESTED.equals(batch.getStatus())) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "已出池结转，差异单不能审核通过改写已结转成本");
        }

        if (approve) {
            CostBalance balance = getOrCreateBalance(batch, adjustment.getCostType());
            BigDecimal delta = CostAdjustment.DIRECTION_ADD.equals(adjustment.getDirection())
                    ? adjustment.getAmount() : adjustment.getAmount().negate();
            BigDecimal after = nz(balance.getSettledAmount()).add(delta);
            if (after.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException(
                        "核减差异" + adjustment.getAmount() + "超过当前未结余额"
                                + nz(balance.getSettledAmount()) + "，不能审核通过");
            }
            adjustment.setStatus(CostAdjustment.STATUS_APPROVED);
            adjustment.setReviewedBy(request.getReviewedBy());
            adjustment.setReviewRemark(request.getReviewRemark());
            adjustment.setReviewedTime(LocalDateTime.now());
            adjustmentMapper.updateById(adjustment);

            balance.setSettledAmount(after);
            syncStock(balance, batch);
            balanceMapper.updateById(balance);

            CostEntry entry = new CostEntry();
            entry.setEntryNo("CE-ADJ-" + adjustment.getId());
            entry.setPondId(batch.getPondId());
            entry.setBatchId(batch.getId());
            entry.setCostType(adjustment.getCostType());
            entry.setAmount(adjustment.getAmount());
            entry.setOccurDate(adjustment.getOccurDate());
            entry.setSource(CostEntry.SOURCE_ADJUSTMENT);
            entry.setVoucherId(adjustment.getVoucherId());
            entry.setVoucherNo(adjustment.getVoucherNo());
            entry.setAdjustmentId(adjustment.getId());
            entry.setBatchStatusAtEntry(batch.getStatus());
            entry.setRequestId(request.getRequestId() + "-" + adjustment.getId());
            entry.setOperator(request.getReviewedBy());
            entry.setRemark("差异调整单" + adjustment.getAdjNo() + "审核通过入账：" + adjustment.getReason());
            entryMapper.insert(entry);

            evidenceService.record(CostChangeEvidence.ENTITY_ADJUSTMENT, adjustment.getId(),
                    CostChangeEvidence.ACTION_APPROVE, request.getRequestId(), request.getReviewedBy(),
                    null, adjustment,
                    "差异调整审核通过并入账：" + adjustment.getAdjNo() + "，方向="
                            + adjustment.getDirection() + "，金额=" + adjustment.getAmount()
                            + "，审核后余额=" + after);
        } else {
            adjustment.setStatus(CostAdjustment.STATUS_REJECTED);
            adjustment.setReviewedBy(request.getReviewedBy());
            adjustment.setReviewRemark(request.getReviewRemark());
            adjustment.setReviewedTime(LocalDateTime.now());
            adjustmentMapper.updateById(adjustment);
            evidenceService.record(CostChangeEvidence.ENTITY_ADJUSTMENT, adjustment.getId(),
                    CostChangeEvidence.ACTION_REJECT, request.getRequestId(), request.getReviewedBy(),
                    null, adjustment,
                    "差异调整驳回，账面不变：" + adjustment.getAdjNo() + "，意见=" + request.getReviewRemark());
        }
        return adjustment;
    }

    // ============================================================
    // 查询
    // ============================================================

    public List<CostEntry> listEntries(Long pondId, Long batchId, String costType) {
        QueryWrapper<CostEntry> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(costType != null && !costType.trim().isEmpty(), "cost_type", costType);
        wrapper.orderByAsc("id");
        return entryMapper.selectList(wrapper);
    }

    /**
     * 凭条列表角色视图：财务返回完整凭条（含金额、凭证号）；
     * 生产角色只看池/批/类型/来源/日期等汇总信息，金额与凭证号不下发。
     */
    public List<Map<String, Object>> listEntriesView(Long pondId, Long batchId, String costType, String role) {
        boolean finance = ROLE_FINANCE.equalsIgnoreCase(role);
        List<Map<String, Object>> view = new ArrayList<>();
        for (CostEntry entry : listEntries(pondId, batchId, costType)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("entryNo", entry.getEntryNo());
            map.put("pondId", entry.getPondId());
            map.put("batchId", entry.getBatchId());
            map.put("costType", entry.getCostType());
            map.put("source", entry.getSource());
            map.put("occurDate", entry.getOccurDate());
            map.put("remark", entry.getRemark());
            if (finance) {
                map.put("amount", entry.getAmount());
                map.put("voucherId", entry.getVoucherId());
                map.put("voucherNo", entry.getVoucherNo());
            }
            view.add(map);
        }
        return view;
    }

    public List<CostAdjustment> listAdjustments(Long batchId, String status) {
        QueryWrapper<CostAdjustment> wrapper = new QueryWrapper<>();
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return adjustmentMapper.selectList(wrapper);
    }

    /**
     * 差异单列表角色视图：生产角色看不到差异金额与凭证号，只看到单状态/类型/原因等流程信息。
     */
    public List<Map<String, Object>> listAdjustmentsView(Long batchId, String status, String role) {
        boolean finance = ROLE_FINANCE.equalsIgnoreCase(role);
        List<Map<String, Object>> view = new ArrayList<>();
        for (CostAdjustment adjustment : listAdjustments(batchId, status)) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("adjNo", adjustment.getAdjNo());
            map.put("batchId", adjustment.getBatchId());
            map.put("pondId", adjustment.getPondId());
            map.put("costType", adjustment.getCostType());
            map.put("direction", adjustment.getDirection());
            map.put("occurDate", adjustment.getOccurDate());
            map.put("reason", adjustment.getReason());
            map.put("status", adjustment.getStatus());
            map.put("applicant", adjustment.getApplicant());
            map.put("reviewedBy", adjustment.getReviewedBy());
            if (finance) {
                map.put("amount", adjustment.getAmount());
                map.put("voucherNo", adjustment.getVoucherNo());
            }
            view.add(map);
        }
        return view;
    }

    public CostAdjustment getAdjustmentOrThrow(Long id) {
        CostAdjustment adjustment = adjustmentMapper.selectById(id);
        if (adjustment == null) {
            throw new IllegalArgumentException("差异调整单不存在，id=" + id);
        }
        return adjustment;
    }

    public CostCarryover getCarryoverByBatch(Long batchId) {
        return carryoverMapper.selectOne(new QueryWrapper<CostCarryover>().eq("batch_id", batchId));
    }

    public List<CostCarryoverLine> listCarryoverLines(Long carryoverId) {
        return carryoverLineMapper.selectList(
                new QueryWrapper<CostCarryoverLine>().eq("carryover_id", carryoverId).orderByAsc("id"));
    }

    // ============================================================
    // 内部工具
    // ============================================================

    private void requireCostType(String costType) {
        if (!COST_TYPES.contains(costType)) {
            throw new IllegalArgumentException("费用类型只能是 FEED/LABOR/MED/ENERGY，收到：" + costType);
        }
    }

    private CostBalance getOrCreateBalance(SeedlingBatch batch, String costType) {
        CostBalance balance = balanceMapper.selectOne(new QueryWrapper<CostBalance>()
                .eq("batch_id", batch.getId()).eq("cost_type", costType));
        if (balance == null) {
            balance = new CostBalance();
            balance.setBatchId(batch.getId());
            balance.setPondId(batch.getPondId());
            balance.setCostType(costType);
            balance.setQuantity(batch.getQuantity() == null ? 0 : batch.getQuantity());
            balance.setBiomassKg(nz(batch.getBiomassKg()));
            balance.setSettledAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            balance.setCarryoverInAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            balance.setCarryoverOutAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            balanceMapper.insert(balance);
        }
        return balance;
    }

    private void syncStock(CostBalance balance, SeedlingBatch batch) {
        balance.setPondId(batch.getPondId());
        balance.setQuantity(batch.getQuantity() == null ? 0 : batch.getQuantity());
        balance.setBiomassKg(nz(batch.getBiomassKg()));
    }

    private CostTransferLine newTransferLine(Long transferId, Long sourceBatchId, Long receiveBatchId,
                                             Long receivePondId, String costType, int quantity,
                                             BigDecimal biomass, BigDecimal weight, BigDecimal amount) {
        CostTransferLine line = new CostTransferLine();
        line.setTransferId(transferId);
        line.setSourceBatchId(sourceBatchId);
        line.setReceiveBatchId(receiveBatchId);
        line.setReceivePondId(receivePondId);
        line.setCostType(costType);
        line.setQuantity(quantity);
        line.setBiomassKg(nz(biomass));
        line.setWeight(weight);
        line.setAllocatedAmount(amount);
        return line;
    }

    private Map<String, Object> lineSnapshot(CostTransferLine line) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("receiveBatchId", line.getReceiveBatchId());
        map.put("receivePondId", line.getReceivePondId());
        map.put("costType", line.getCostType());
        map.put("quantity", line.getQuantity());
        map.put("biomassKg", line.getBiomassKg());
        map.put("weight", line.getWeight());
        map.put("allocatedAmount", line.getAllocatedAmount());
        return map;
    }

    private String nextEntryNo(Long batchId, LocalDate date) {
        Long count = entryMapper.selectCount(new QueryWrapper<CostEntry>()
                .eq("batch_id", batchId).eq("occur_date", date));
        return "CE-" + batchId + "-" + date.format(DAY) + "-" + ((count == null ? 0 : count) + 1);
    }

    private String nextAdjNo(Long batchId) {
        Long count = adjustmentMapper.selectCount(new QueryWrapper<CostAdjustment>().eq("batch_id", batchId));
        return "CA-" + batchId + "-" + ((count == null ? 0 : count) + 1);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value;
    }
}
