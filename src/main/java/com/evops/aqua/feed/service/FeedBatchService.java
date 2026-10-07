package com.evops.aqua.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.feed.dto.FeedBatchStockRequest;
import com.evops.aqua.feed.entity.FeedBatch;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.mapper.FeedBatchMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 饲料批号：入库登记与三量账（入库/锁定占用/已消耗）。
 * 投喂单锁定时按 FEFO（先到期先用）从未过期、库存充足的批号分配占用；
 * 批号过期或可用库存不足一律拦截。批号推进受权限开关统一闸门控制。
 */
@Service
public class FeedBatchService {

    private final FeedBatchMapper batchMapper;
    private final FeedAccessPolicyService policyService;
    private final FeedEvidenceService evidenceService;

    public FeedBatchService(FeedBatchMapper batchMapper,
                            FeedAccessPolicyService policyService,
                            FeedEvidenceService evidenceService) {
        this.batchMapper = batchMapper;
        this.policyService = policyService;
        this.evidenceService = evidenceService;
    }

    /**
     * 批号入库。权限收紧时批号停止推进，禁止登记新批号。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedBatch stock(FeedBatchStockRequest request) {
        policyService.requireBatchAdvanceEnabled("登记饲料批号");
        Long duplicated = batchMapper.selectCount(
                new QueryWrapper<FeedBatch>().eq("batch_no", request.getBatchNo()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("饲料批号已存在：" + request.getBatchNo());
        }
        if (request.getProductionDate() != null
                && request.getExpiryDate().isBefore(request.getProductionDate())) {
            throw new IllegalArgumentException("到期日期不能早于生产日期");
        }
        FeedBatch batch = new FeedBatch();
        batch.setBatchNo(request.getBatchNo());
        batch.setFeedName(request.getFeedName());
        batch.setSupplier(request.getSupplier());
        batch.setQuantityIn(request.getQuantityIn());
        batch.setQuantityLocked(BigDecimal.ZERO);
        batch.setQuantityConsumed(BigDecimal.ZERO);
        batch.setProductionDate(request.getProductionDate());
        batch.setExpiryDate(request.getExpiryDate());
        batch.setStatus(FeedBatch.STATUS_IN_STOCK);
        batch.setRemark(request.getRemark());
        batchMapper.insert(batch);
        evidenceService.record(FeedChangeEvidence.ENTITY_BATCH, batch.getId(),
                FeedChangeEvidence.ACTION_CREATE, null, null, batch,
                "饲料批号入库：" + batch.getFeedName() + " " + batch.getQuantityIn() + "kg，到期日"
                        + batch.getExpiryDate());
        return batch;
    }

    public FeedBatch getOrThrow(Long id) {
        FeedBatch batch = batchMapper.selectById(id);
        if (batch == null) {
            throw new IllegalArgumentException("饲料批号不存在，id=" + id);
        }
        return batch;
    }

    public List<FeedBatch> list(String feedName) {
        QueryWrapper<FeedBatch> wrapper = new QueryWrapper<>();
        wrapper.eq(feedName != null && !feedName.trim().isEmpty(), "feed_name", feedName);
        wrapper.orderByAsc("expiry_date", "id");
        return batchMapper.selectList(wrapper);
    }

    /**
     * 按 FEFO 为某饲料分配所需数量：优先占用最早到期的可用批号，可跨批号拼配。
     * 只取未过期（到期日 &gt;= 投喂日）且仍有可用量的批号。
     * 全部批号都过期或可用量不足时拦截。
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Allocation> allocate(String feedName, BigDecimal needQty, LocalDate feedDate) {
        List<FeedBatch> candidates = batchMapper.selectList(new QueryWrapper<FeedBatch>()
                .eq("feed_name", feedName)
                .ge("expiry_date", feedDate)
                .orderByAsc("expiry_date", "id"));
        List<FeedBatch> usable = new ArrayList<>();
        BigDecimal totalAvailable = BigDecimal.ZERO;
        for (FeedBatch batch : candidates) {
            if (batch.availableQuantity().compareTo(BigDecimal.ZERO) > 0) {
                usable.add(batch);
                totalAvailable = totalAvailable.add(batch.availableQuantity());
            }
        }
        if (usable.isEmpty()) {
            throw new IllegalArgumentException(
                    "饲料[" + feedName + "]在" + feedDate + "无未过期且有库存的批号可用");
        }
        if (totalAvailable.compareTo(needQty) < 0) {
            throw new IllegalArgumentException(
                    "饲料[" + feedName + "]可用库存不足：需要" + needQty + "kg，未过期批号合计可用"
                            + totalAvailable + "kg");
        }
        List<Allocation> allocations = new ArrayList<>();
        BigDecimal remain = needQty;
        for (FeedBatch batch : usable) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal take = batch.availableQuantity().min(remain);
            if (take.compareTo(BigDecimal.ZERO) > 0) {
                allocations.add(new Allocation(batch.getId(), take));
                remain = remain.subtract(take);
            }
        }
        return allocations;
    }

    /** 投喂单锁定：占用批号库存（locked += qty）。 */
    @Transactional(rollbackFor = Exception.class)
    public void lockQuantity(Long batchId, BigDecimal qty) {
        FeedBatch before = copyOf(getOrThrow(batchId));
        FeedBatch batch = getOrThrow(batchId);
        if (batch.availableQuantity().compareTo(qty) < 0) {
            throw new IllegalArgumentException("饲料批号" + batch.getBatchNo()
                    + "可用库存不足：需要" + qty + "kg，可用" + batch.availableQuantity() + "kg");
        }
        batch.setQuantityLocked(batch.getQuantityLocked().add(qty));
        batchMapper.updateById(batch);
        evidenceService.record(FeedChangeEvidence.ENTITY_BATCH, batch.getId(),
                FeedChangeEvidence.ACTION_LOCK, null, before, batch,
                "投喂单锁定占用" + qty + "kg，占用后可用" + batch.availableQuantity() + "kg");
    }

    /** 临时减量/解锁：释放已占用但不再投喂的数量（locked -= qty）。 */
    @Transactional(rollbackFor = Exception.class)
    public void releaseLocked(Long batchId, BigDecimal qty) {
        FeedBatch before = copyOf(getOrThrow(batchId));
        FeedBatch batch = getOrThrow(batchId);
        if (batch.getQuantityLocked().compareTo(qty) < 0) {
            throw new IllegalArgumentException("饲料批号" + batch.getBatchNo()
                    + "锁定占用量不足，无法释放" + qty + "kg，当前占用" + batch.getQuantityLocked() + "kg");
        }
        batch.setQuantityLocked(batch.getQuantityLocked().subtract(qty));
        batchMapper.updateById(batch);
        evidenceService.record(FeedChangeEvidence.ENTITY_BATCH, batch.getId(),
                FeedChangeEvidence.ACTION_UNLOCK, null, before, batch,
                "释放锁定占用" + qty + "kg，释放后可用" + batch.availableQuantity() + "kg");
    }

    /**
     * 投喂单执行：把锁定占用转为实际消耗（locked -= qty, consumed += qty），
     * 可用量清零的批号置为 EXHAUSTED。权限收紧时批号停止推进，禁止执行消耗。
     */
    @Transactional(rollbackFor = Exception.class)
    public void consumeLocked(Long batchId, BigDecimal qty) {
        policyService.requireBatchAdvanceEnabled("执行投喂消耗饲料批号");
        FeedBatch before = copyOf(getOrThrow(batchId));
        FeedBatch batch = getOrThrow(batchId);
        if (batch.getQuantityLocked().compareTo(qty) < 0) {
            throw new IllegalArgumentException("饲料批号" + batch.getBatchNo()
                    + "锁定占用量不足，无法消耗" + qty + "kg，当前占用" + batch.getQuantityLocked() + "kg");
        }
        batch.setQuantityLocked(batch.getQuantityLocked().subtract(qty));
        batch.setQuantityConsumed(batch.getQuantityConsumed().add(qty));
        if (batch.availableQuantity().compareTo(BigDecimal.ZERO) == 0) {
            batch.setStatus(FeedBatch.STATUS_EXHAUSTED);
        }
        batchMapper.updateById(batch);
        evidenceService.record(FeedChangeEvidence.ENTITY_BATCH, batch.getId(),
                FeedChangeEvidence.ACTION_EXECUTE, null, before, batch,
                "投喂执行消耗" + qty + "kg，累计消耗" + batch.getQuantityConsumed() + "kg");
    }

    private FeedBatch copyOf(FeedBatch source) {
        FeedBatch copy = new FeedBatch();
        copy.setId(source.getId());
        copy.setBatchNo(source.getBatchNo());
        copy.setFeedName(source.getFeedName());
        copy.setSupplier(source.getSupplier());
        copy.setQuantityIn(source.getQuantityIn());
        copy.setQuantityLocked(source.getQuantityLocked());
        copy.setQuantityConsumed(source.getQuantityConsumed());
        copy.setProductionDate(source.getProductionDate());
        copy.setExpiryDate(source.getExpiryDate());
        copy.setStatus(source.getStatus());
        copy.setRemark(source.getRemark());
        return copy;
    }

    /** 一条批号分配：批号 id + 本次占用数量。 */
    public static class Allocation {
        private final Long batchId;
        private final BigDecimal quantityKg;

        public Allocation(Long batchId, BigDecimal quantityKg) {
            this.batchId = batchId;
            this.quantityKg = quantityKg;
        }

        public Long getBatchId() { return batchId; }
        public BigDecimal getQuantityKg() { return quantityKg; }
    }
}
