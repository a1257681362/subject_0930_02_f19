package com.evops.aqua.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.service.IsolationGuard;
import com.evops.aqua.dto.BatchStockRequest;
import com.evops.aqua.dto.BiomassUpdateRequest;
import com.evops.aqua.dto.HarvestRequest;
import com.evops.aqua.dto.TransferRequest;
import com.evops.aqua.entity.HarvestRecord;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.entity.TransferRecord;
import com.evops.aqua.event.BatchHarvestedEvent;
import com.evops.aqua.event.BatchTransferredEvent;
import com.evops.aqua.mapper.HarvestRecordMapper;
import com.evops.aqua.mapper.SeedlingBatchMapper;
import com.evops.aqua.mapper.TransferRecordMapper;
import com.evops.aqua.med.service.MedWithdrawalGuard;
import com.evops.aqua.noculture.service.NoCultureGuard;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 投苗批次（原始投苗单）。
 * 注意：不提供任何删除方法——原始投苗单不可删除。
 * 隔离守卫：隔离中的批次禁止换池（混养）与出池；隔离池在隔离期间禁止投苗混养。
 * 海区禁养守卫：投苗/转池/捕捞三入口做空间 + 时间校验；被禁养规则冻结的在途批次一律拦截，
 * 空间重叠命中多条规则时按更严格规则执行。
 * 换池/出池成功后发布领域事件，成本模块在同一事务内完成未结成本分配与结转，
 * 任一步失败整体回滚，台账与成本账不会出现中间态。
 */
@Service
public class SeedlingBatchService {

    private final SeedlingBatchMapper batchMapper;
    private final PondService pondService;
    private final TransferRecordMapper transferRecordMapper;
    private final HarvestRecordMapper harvestRecordMapper;
    private final IsolationGuard isolationGuard;
    private final MedWithdrawalGuard medWithdrawalGuard;
    private final NoCultureGuard noCultureGuard;
    private final ApplicationEventPublisher eventPublisher;

    public SeedlingBatchService(SeedlingBatchMapper batchMapper,
                                PondService pondService,
                                TransferRecordMapper transferRecordMapper,
                                HarvestRecordMapper harvestRecordMapper,
                                IsolationGuard isolationGuard,
                                MedWithdrawalGuard medWithdrawalGuard,
                                NoCultureGuard noCultureGuard,
                                ApplicationEventPublisher eventPublisher) {
        this.batchMapper = batchMapper;
        this.pondService = pondService;
        this.transferRecordMapper = transferRecordMapper;
        this.harvestRecordMapper = harvestRecordMapper;
        this.isolationGuard = isolationGuard;
        this.medWithdrawalGuard = medWithdrawalGuard;
        this.noCultureGuard = noCultureGuard;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 投苗：生成原始投苗单，批次编号全局唯一。
     */
    public SeedlingBatch stock(BatchStockRequest request) {
        Pond pond = pondService.getOrThrow(request.getPondId());
        pondService.requireActive(pond);
        isolationGuard.requirePondOpenForStocking(pond.getId());
        noCultureGuard.requireActionAllowed(pond.getId(), request.getStockDate(), "投苗");
        Long duplicated = batchMapper.selectCount(
                new QueryWrapper<SeedlingBatch>().eq("batch_no", request.getBatchNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("批次编号已存在：" + request.getBatchNo());
        }
        if (request.getPlannedHarvestDate().isBefore(request.getStockDate())) {
            throw new IllegalArgumentException("计划出池日不能早于投苗日期");
        }
        SeedlingBatch batch = new SeedlingBatch();
        BeanUtils.copyProperties(request, batch);
        if (batch.getBiomassKg() == null) {
            batch.setBiomassKg(BigDecimal.ZERO);
        }
        batch.setStatus(SeedlingBatch.STATUS_STOCKED);
        batchMapper.insert(batch);
        return batch;
    }

    /**
     * 换池：支持整批转移与分池。
     * 不传 quantity 或数量等于在养数量——整批转移，批次移到目标池；
     * 数量小于在养数量——分池，源批次核减留存、目标池派生接收批次。
     * 两种方式都生成转移记录（含转出部分生物量）并发布换池事件供成本分配。
     */
    @Transactional(rollbackFor = Exception.class)
    public TransferRecord transfer(Long batchId, TransferRequest request) {
        SeedlingBatch batch = getOrThrow(batchId);
        requireStocked(batch, "换池");
        isolationGuard.requireBatchNotIsolated(batchId, "换池（混养）");
        noCultureGuard.requireBatchNotFrozen(batchId, request.getTransferDate(), "换池");
        Pond target = pondService.getOrThrow(request.getToPondId());
        pondService.requireActive(target);
        noCultureGuard.requireActionAllowed(target.getId(), request.getTransferDate(), "换池入池");
        if (target.getId().equals(batch.getPondId())) {
            throw new IllegalArgumentException("目标池与当前池相同，无需换池");
        }

        int currentQty = batch.getQuantity() == null ? 0 : batch.getQuantity();
        int movedQty = request.getQuantity() == null ? currentQty : request.getQuantity();
        if (movedQty <= 0) {
            throw new IllegalArgumentException("转池数量必须大于0");
        }
        if (movedQty > currentQty) {
            throw new IllegalArgumentException(
                    "转池数量" + movedQty + "超过批次在养数量" + currentQty);
        }
        boolean split = movedQty < currentQty;

        BigDecimal totalBio = batch.getBiomassKg() == null ? BigDecimal.ZERO : batch.getBiomassKg();
        BigDecimal movedBio;
        if (request.getBiomassKg() != null) {
            movedBio = request.getBiomassKg();
            if (split && movedBio.compareTo(totalBio) > 0) {
                throw new IllegalArgumentException("转出生物量不能超过批次当前生物量" + totalBio);
            }
        } else if (split && currentQty > 0) {
            // 未指定转出生物量时按数量比例折算，保留三位小数
            movedBio = totalBio.multiply(BigDecimal.valueOf(movedQty))
                    .divide(BigDecimal.valueOf(currentQty), 3, RoundingMode.HALF_UP);
        } else {
            movedBio = totalBio;
        }
        BigDecimal remainBio = totalBio.subtract(movedBio);

        TransferRecord record = new TransferRecord();
        record.setBatchId(batch.getId());
        record.setFromPondId(batch.getPondId());
        record.setToPondId(target.getId());
        record.setTransferDate(request.getTransferDate());
        record.setQuantity(movedQty);
        record.setBiomassKg(movedBio);
        record.setReason(request.getReason());
        transferRecordMapper.insert(record);

        Long receiveBatchId;
        String receiveBatchNo = null;
        int remainQty;
        if (split) {
            remainQty = currentQty - movedQty;
            batch.setQuantity(remainQty);
            batch.setBiomassKg(remainBio);
            batchMapper.updateById(batch);

            SeedlingBatch child = new SeedlingBatch();
            child.setBatchNo(nextSplitBatchNo(batch));
            child.setPondId(target.getId());
            child.setSpecies(batch.getSpecies());
            child.setSeedSource(batch.getSeedSource() + "（分池自" + batch.getBatchNo() + "）");
            child.setQuantity(movedQty);
            child.setBiomassKg(movedBio);
            child.setStockDate(request.getTransferDate());
            child.setPlannedHarvestDate(batch.getPlannedHarvestDate());
            child.setStatus(SeedlingBatch.STATUS_STOCKED);
            child.setSplitParentBatchId(batch.getId());
            child.setRemark("由批次" + batch.getBatchNo() + "分池派生");
            batchMapper.insert(child);
            receiveBatchId = child.getId();
            receiveBatchNo = child.getBatchNo();
        } else {
            remainQty = 0;
            batch.setPondId(target.getId());
            batch.setBiomassKg(movedBio);
            batchMapper.updateById(batch);
            receiveBatchId = batch.getId();
            receiveBatchNo = batch.getBatchNo();
        }

        eventPublisher.publishEvent(new BatchTransferredEvent(
                record.getId(), batch.getId(), receiveBatchId, receiveBatchNo,
                record.getFromPondId(), target.getId(),
                request.getTransferDate(),
                movedQty, remainQty, movedBio, remainBio, split,
                "TRANSFER-" + record.getId(), currentOperator()));
        return record;
    }

    /**
     * 出池：生成出池记录，批次状态置为已出池，台账封存；
     * 同事务发布出池事件，成本模块结转未结成本并形成快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public HarvestRecord harvest(Long batchId, HarvestRequest request) {
        SeedlingBatch batch = getOrThrow(batchId);
        requireStocked(batch, "出池");
        isolationGuard.requireBatchNotIsolated(batchId, "出池");
        noCultureGuard.requireBatchNotFrozen(batchId, request.getHarvestDate(), "捕捞（出池）");
        noCultureGuard.requireActionAllowed(batch.getPondId(), request.getHarvestDate(), "捕捞（出池）");
        medWithdrawalGuard.requireHarvestAllowed(batch.getPondId(), request.getHarvestDate());
        HarvestRecord record = new HarvestRecord();
        record.setBatchId(batch.getId());
        record.setPondId(batch.getPondId());
        record.setHarvestDate(request.getHarvestDate());
        record.setQuantity(request.getQuantity());
        record.setRemark(request.getRemark());
        harvestRecordMapper.insert(record);

        batch.setStatus(SeedlingBatch.STATUS_HARVESTED);
        batchMapper.updateById(batch);

        eventPublisher.publishEvent(new BatchHarvestedEvent(
                batch.getId(), batch.getPondId(), record.getId(),
                "HARVEST-" + record.getId(), currentOperator()));
        return record;
    }

    /**
     * 更新批次估计生物量：转池成本按数量与生物量分配，生物量以最近核定为准。
     */
    public SeedlingBatch updateBiomass(Long batchId, BiomassUpdateRequest request) {
        SeedlingBatch batch = getOrThrow(batchId);
        requireStocked(batch, "核定生物量");
        if (request.getBiomassKg() == null || request.getBiomassKg().signum() < 0) {
            throw new IllegalArgumentException("生物量不能为空且不能为负");
        }
        batch.setBiomassKg(request.getBiomassKg());
        batchMapper.updateById(batch);
        return batch;
    }

    public SeedlingBatch getOrThrow(Long id) {
        SeedlingBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new IllegalArgumentException("投苗批次不存在，id=" + id);
        }
        return batch;
    }

    public List<SeedlingBatch> list(QueryWrapper<SeedlingBatch> wrapper) {
        return batchMapper.selectList(wrapper);
    }

    public void requireStocked(SeedlingBatch batch, String action) {
        if (!SeedlingBatch.STATUS_STOCKED.equals(batch.getStatus())) {
            throw new IllegalArgumentException("批次" + batch.getBatchNo() + "已出池，不能" + action);
        }
    }

    private String nextSplitBatchNo(SeedlingBatch parent) {
        Long count = batchMapper.selectCount(new QueryWrapper<SeedlingBatch>()
                .eq("split_parent_batch_id", parent.getId()));
        return parent.getBatchNo() + "-S" + ((count == null ? 0 : count) + 1);
    }

    private String currentOperator() {
        try {
            Object principal = SecurityUtils.getSubject().getPrincipal();
            return principal == null ? "SYSTEM" : String.valueOf(principal);
        } catch (Exception ex) {
            return "SYSTEM";
        }
    }
}
