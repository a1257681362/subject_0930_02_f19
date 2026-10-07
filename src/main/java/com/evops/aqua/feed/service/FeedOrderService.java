package com.evops.aqua.feed.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.entity.SeedlingBatch;
import com.evops.aqua.feed.dto.OrderPrepareRequest;
import com.evops.aqua.feed.dto.ReductionRequest;
import com.evops.aqua.feed.entity.FeedBatch;
import com.evops.aqua.feed.entity.FeedBatchLock;
import com.evops.aqua.feed.entity.FeedChangeEvidence;
import com.evops.aqua.feed.entity.FeedFormulaRule;
import com.evops.aqua.feed.entity.FeedFormulaVersion;
import com.evops.aqua.feed.entity.FeedOrder;
import com.evops.aqua.feed.entity.FeedOrderItem;
import com.evops.aqua.feed.mapper.FeedBatchLockMapper;
import com.evops.aqua.feed.mapper.FeedOrderItemMapper;
import com.evops.aqua.feed.mapper.FeedOrderMapper;
import com.evops.aqua.service.PondService;
import com.evops.aqua.service.SeedlingBatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 投喂单：准备（按苗种/日龄/水温匹配当前生效配方并冻结版本快照）→ 锁定（按 FEFO 占用未过期批号）
 * → 临时减量（登记原因与审批人并释放差额占用）→ 执行（锁定占用转实际消耗）。
 * 投喂单只有锁定后才允许执行；试喂与正式投喂分开建单、日汇总分列。
 */
@Service
public class FeedOrderService {

    private final FeedOrderMapper orderMapper;
    private final FeedOrderItemMapper itemMapper;
    private final FeedBatchLockMapper lockMapper;
    private final FeedBatchService batchService;
    private final FeedFormulaService formulaService;
    private final FeedAccessPolicyService policyService;
    private final FeedEvidenceService evidenceService;
    private final SeedlingBatchService seedlingBatchService;
    private final PondService pondService;

    public FeedOrderService(FeedOrderMapper orderMapper,
                            FeedOrderItemMapper itemMapper,
                            FeedBatchLockMapper lockMapper,
                            FeedBatchService batchService,
                            FeedFormulaService formulaService,
                            FeedAccessPolicyService policyService,
                            FeedEvidenceService evidenceService,
                            SeedlingBatchService seedlingBatchService,
                            PondService pondService) {
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.lockMapper = lockMapper;
        this.batchService = batchService;
        this.formulaService = formulaService;
        this.policyService = policyService;
        this.evidenceService = evidenceService;
        this.seedlingBatchService = seedlingBatchService;
        this.pondService = pondService;
    }

    /**
     * 准备投喂单：批次须在养且当前在该池；日龄由投苗日与投喂日推算；
     * 从该苗种当前生效配方按日龄+水温命中规则，建立即冻结配方版本与计划量快照。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedOrder prepare(OrderPrepareRequest request) {
        SeedlingBatch batch = requireBatchInPond(request.getBatchId(), request.getPondId());
        long dayAgeLong = ChronoUnit.DAYS.between(batch.getStockDate(), request.getFeedDate());
        if (dayAgeLong < 0) {
            throw new IllegalArgumentException("投喂日期不能早于投苗日期：" + batch.getStockDate());
        }
        int dayAge = (int) dayAgeLong;
        String feedType = FeedOrder.TYPE_TRIAL.equals(request.getFeedType())
                ? FeedOrder.TYPE_TRIAL : FeedOrder.TYPE_FORMAL;

        // 选择当前生效配方并冻结版本指针；历史投喂单此后不受新版本影响
        FeedFormulaVersion version = formulaService.currentActive(batch.getSpecies());
        FeedFormulaRule rule = formulaService.matchRule(
                version.getId(), dayAge, request.getWaterTempC());

        // 同池同批同日同类型只允许一张投喂单：重复准备幂等返回，不产生第二条
        String orderNo = "FO-" + request.getPondId() + "-" + request.getBatchId() + "-"
                + request.getFeedDate().toString().replace("-", "") + "-" + feedType;
        List<FeedOrder> same = orderMapper.selectList(new QueryWrapper<FeedOrder>()
                .eq("order_no", orderNo));
        if (!same.isEmpty()) {
            return same.get(0);
        }

        FeedOrder order = new FeedOrder();
        order.setOrderNo(orderNo);
        order.setPondId(request.getPondId());
        order.setBatchId(request.getBatchId());
        order.setFeedDate(request.getFeedDate());
        order.setDayAge(dayAge);
        order.setWaterTempC(request.getWaterTempC());
        order.setFeedType(feedType);
        order.setFormulaVersionId(version.getId());
        order.setStatus(FeedOrder.STATUS_DRAFT);
        order.setRemark(request.getRemark());
        orderMapper.insert(order);

        FeedOrderItem item = new FeedOrderItem();
        item.setOrderId(order.getId());
        item.setFeedName(rule.getFeedName());
        item.setPlannedQtyKg(rule.getPlannedQtyKg());
        item.setReductionFlag(Boolean.FALSE);
        itemMapper.insert(item);

        evidenceService.record(FeedChangeEvidence.ENTITY_ORDER, order.getId(),
                FeedChangeEvidence.ACTION_CREATE, request.getRequestId(), null, order,
                (feedType.equals(FeedOrder.TYPE_TRIAL) ? "试喂" : "正式") + "投喂单准备：苗种"
                        + batch.getSpecies() + "，日龄" + dayAge + "，水温" + request.getWaterTempC()
                        + "，命中配方版本" + version.getVersionNo() + "，饲料" + rule.getFeedName()
                        + "计划" + rule.getPlannedQtyKg() + "kg（版本快照已冻结）");
        return order;
    }

    /**
     * 锁定投喂单：只有草稿可锁定。按 FEFO 为每个明细占用未过期批号库存；
     * 批号过期或可用库存不足在此拦截。权限收紧时批号停止推进，禁止锁定占用。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedOrder lock(Long orderId, String requestId) {
        FeedOrder order = getOrThrow(orderId);
        if (FeedOrder.STATUS_LOCKED.equals(order.getStatus())
                || FeedOrder.STATUS_EXECUTED.equals(order.getStatus())) {
            return order; // 幂等：重复锁定不重复占用
        }
        if (!FeedOrder.STATUS_DRAFT.equals(order.getStatus())) {
            throw new IllegalArgumentException("当前投喂单状态不能锁定：" + order.getStatus());
        }
        policyService.requireBatchAdvanceEnabled("锁定投喂单占用饲料批号");

        for (FeedOrderItem item : listItems(orderId)) {
            List<FeedBatchService.Allocation> allocations =
                    batchService.allocate(item.getFeedName(), item.getPlannedQtyKg(), order.getFeedDate());
            for (FeedBatchService.Allocation allocation : allocations) {
                batchService.lockQuantity(allocation.getBatchId(), allocation.getQuantityKg());
                FeedBatchLock lock = new FeedBatchLock();
                lock.setFeedBatchId(allocation.getBatchId());
                lock.setOrderItemId(item.getId());
                lock.setQuantityKg(allocation.getQuantityKg());
                lock.setStatus(FeedBatchLock.STATUS_LOCKED);
                lockMapper.insert(lock);
            }
            if (allocations.size() == 1) {
                item.setFeedBatchId(allocations.get(0).getBatchId());
                itemMapper.updateById(item);
            }
        }

        FeedOrder before = copyOf(order);
        order.setStatus(FeedOrder.STATUS_LOCKED);
        order.setLockedTime(LocalDateTime.now());
        orderMapper.updateById(order);
        evidenceService.record(FeedChangeEvidence.ENTITY_ORDER, order.getId(),
                FeedChangeEvidence.ACTION_LOCK, requestId, before, order,
                "投喂单锁定，饲料批号已按先到期先用占用库存");
        return order;
    }

    /**
     * 临时减量：仅锁定态可登记。实际量必须小于计划量，且必须填原因与审批人；
     * 登记后释放（计划-实际）差额的批号占用，执行只消耗减量后的实际量。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedOrderItem reduce(Long orderId, Long itemId, ReductionRequest request) {
        FeedOrder order = getOrThrow(orderId);
        if (!FeedOrder.STATUS_LOCKED.equals(order.getStatus())) {
            throw new IllegalArgumentException("只有已锁定投喂单才能临时减量，当前状态：" + order.getStatus());
        }
        FeedOrderItem item = getItemOrThrow(orderId, itemId);
        BigDecimal lockedSum = lockedSum(itemId);
        BigDecimal actual = request.getActualQtyKg();
        if (actual.compareTo(lockedSum) >= 0) {
            throw new IllegalArgumentException(
                    "临时减量后投喂量" + actual + "kg必须小于当前锁定量" + lockedSum + "kg");
        }
        releaseItemLocks(itemId, lockedSum.subtract(actual));

        item.setActualQtyKg(actual);
        item.setReductionFlag(Boolean.TRUE);
        item.setReductionReason(request.getReductionReason());
        item.setReductionApprovedBy(request.getReductionApprovedBy());
        itemMapper.updateById(item);
        evidenceService.record(FeedChangeEvidence.ENTITY_ORDER_ITEM, item.getId(),
                FeedChangeEvidence.ACTION_REDUCE, request.getRequestId(), null, item,
                "临时减量：计划" + item.getPlannedQtyKg() + "kg -> 实际" + actual + "kg，原因="
                        + request.getReductionReason() + "，审批人=" + request.getReductionApprovedBy());
        return item;
    }

    /**
     * 解锁：把已锁定投喂单退回草稿并释放全部批号占用（锁定信息可重算）。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedOrder unlock(Long orderId, String requestId) {
        FeedOrder order = getOrThrow(orderId);
        if (FeedOrder.STATUS_DRAFT.equals(order.getStatus())) {
            return order;
        }
        if (FeedOrder.STATUS_EXECUTED.equals(order.getStatus())) {
            throw new IllegalArgumentException("投喂单已执行，不能解锁");
        }
        for (FeedOrderItem item : listItems(orderId)) {
            releaseItemLocks(item.getId(), lockedSum(item.getId()));
            item.setActualQtyKg(null);
            item.setFeedBatchId(null);
            item.setReductionFlag(Boolean.FALSE);
            item.setReductionReason(null);
            item.setReductionApprovedBy(null);
            itemMapper.updateById(item);
        }
        FeedOrder before = copyOf(order);
        order.setStatus(FeedOrder.STATUS_DRAFT);
        order.setLockedTime(null);
        order.setLockedBy(null);
        orderMapper.updateById(order);
        evidenceService.record(FeedChangeEvidence.ENTITY_ORDER, order.getId(),
                FeedChangeEvidence.ACTION_UNLOCK, requestId, before, order, "投喂单解锁，批号占用全部释放");
        return order;
    }

    /**
     * 执行投喂：只有锁定后才允许执行。把各明细锁定占用按实际量转为批号消耗。
     * 未登记减量的明细按计划量执行；减量明细按其实际量执行（原因与审批人已登记）。
     * 权限收紧时批号停止推进，禁止执行消耗。
     */
    @Transactional(rollbackFor = Exception.class)
    public FeedOrder execute(Long orderId, String requestId) {
        FeedOrder order = getOrThrow(orderId);
        if (FeedOrder.STATUS_EXECUTED.equals(order.getStatus())) {
            return order; // 幂等：重复执行不重复消耗
        }
        if (!FeedOrder.STATUS_LOCKED.equals(order.getStatus())) {
            throw new IllegalArgumentException(
                    "投喂单必须先锁定才能执行，当前状态：" + order.getStatus() + "（草稿态直接执行应被拒绝）");
        }

        for (FeedOrderItem item : listItems(orderId)) {
            List<FeedBatchLock> locks = activeLocks(item.getId());
            BigDecimal actual = item.getActualQtyKg() != null
                    ? item.getActualQtyKg() : item.getPlannedQtyKg();
            if (actual.compareTo(item.getPlannedQtyKg()) < 0
                    && (!Boolean.TRUE.equals(item.getReductionFlag())
                    || isBlank(item.getReductionReason())
                    || isBlank(item.getReductionApprovedBy()))) {
                throw new IllegalArgumentException(
                        "饲料[" + item.getFeedName() + "]实际量低于计划量，必须先登记减量原因与审批人");
            }
            BigDecimal lockTotal = BigDecimal.ZERO;
            for (FeedBatchLock lock : locks) {
                lockTotal = lockTotal.add(lock.getQuantityKg());
            }
            if (actual.compareTo(lockTotal) != 0) {
                throw new IllegalArgumentException("饲料[" + item.getFeedName()
                        + "]锁定占用" + lockTotal + "kg与实际投喂" + actual + "kg不一致，请解锁后重新锁定或登记减量");
            }
            for (FeedBatchLock lock : locks) {
                FeedBatch batch = batchService.getOrThrow(lock.getFeedBatchId());
                if (batch.getExpiryDate().isBefore(order.getFeedDate())) {
                    throw new IllegalArgumentException("饲料批号" + batch.getBatchNo()
                            + "已于" + batch.getExpiryDate() + "过期，不能执行投喂");
                }
                batchService.consumeLocked(lock.getFeedBatchId(), lock.getQuantityKg());
                lock.setStatus(FeedBatchLock.STATUS_CONSUMED);
                lockMapper.updateById(lock);
            }
            if (item.getActualQtyKg() == null) {
                item.setActualQtyKg(item.getPlannedQtyKg());
            }
            if (locks.size() == 1) {
                item.setFeedBatchId(locks.get(0).getFeedBatchId());
            }
            itemMapper.updateById(item);
        }

        FeedOrder before = copyOf(order);
        order.setStatus(FeedOrder.STATUS_EXECUTED);
        order.setExecutedTime(LocalDateTime.now());
        orderMapper.updateById(order);
        evidenceService.record(FeedChangeEvidence.ENTITY_ORDER, order.getId(),
                FeedChangeEvidence.ACTION_EXECUTE, requestId, before, order,
                (FeedOrder.TYPE_TRIAL.equals(order.getFeedType()) ? "试喂" : "正式")
                        + "投喂执行完成，锁定批号已转为实际消耗");
        return order;
    }

    /**
     * 日汇总：按池按日统计【已执行】投喂，试喂与正式投喂严格分列，绝不混算。
     */
    public Map<String, Object> dailySummary(Long pondId, LocalDate feedDate) {
        List<FeedOrder> orders = orderMapper.selectList(new QueryWrapper<FeedOrder>()
                .eq("pond_id", pondId)
                .eq("feed_date", feedDate)
                .eq("status", FeedOrder.STATUS_EXECUTED)
                .orderByAsc("id"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("pondId", pondId);
        result.put("feedDate", feedDate);
        result.put("formal", summarizeByType(orders, FeedOrder.TYPE_FORMAL));
        result.put("trial", summarizeByType(orders, FeedOrder.TYPE_TRIAL));
        return result;
    }

    public FeedOrder getOrThrow(Long id) {
        FeedOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("投喂单不存在，id=" + id);
        }
        return order;
    }

    public List<FeedOrder> listOrders(Long pondId, Long batchId, String status, String feedType) {
        QueryWrapper<FeedOrder> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(batchId != null, "batch_id", batchId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.eq(feedType != null && !feedType.trim().isEmpty(), "feed_type", feedType);
        wrapper.orderByAsc("feed_date", "id");
        return orderMapper.selectList(wrapper);
    }

    public Map<String, Object> orderDetail(Long orderId) {
        FeedOrder order = getOrThrow(orderId);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("order", order);
        List<Map<String, Object>> itemPayload = new ArrayList<>();
        for (FeedOrderItem item : listItems(orderId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item", item);
            m.put("locks", lockMapper.selectList(new QueryWrapper<FeedBatchLock>()
                    .eq("order_item_id", item.getId()).orderByAsc("id")));
            itemPayload.add(m);
        }
        detail.put("items", itemPayload);
        detail.put("formulaVersion", formulaService.getOrThrow(order.getFormulaVersionId()));
        detail.put("formulaRules", formulaService.listRules(order.getFormulaVersionId()));
        return detail;
    }

    // ------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------

    private Map<String, Object> summarizeByType(List<FeedOrder> orders, String feedType) {
        List<FeedOrder> typed = new ArrayList<>();
        for (FeedOrder o : orders) {
            if (feedType.equals(o.getFeedType())) {
                typed.add(o);
            }
        }
        Map<String, BigDecimal> qtyByFeed = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        for (FeedOrder o : typed) {
            for (FeedOrderItem item : listItems(o.getId())) {
                BigDecimal qty = item.getActualQtyKg() != null
                        ? item.getActualQtyKg() : item.getPlannedQtyKg();
                total = total.add(qty);
                qtyByFeed.merge(item.getFeedName(), qty, BigDecimal::add);
            }
        }
        List<Map<String, Object>> byFeed = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : qtyByFeed.entrySet()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("feedName", e.getKey());
            m.put("quantityKg", e.getValue());
            byFeed.add(m);
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("feedType", feedType);
        summary.put("orderCount", typed.size());
        summary.put("totalQuantityKg", total);
        summary.put("byFeed", byFeed);
        return summary;
    }

    private void releaseItemLocks(Long itemId, BigDecimal amountToRelease) {
        BigDecimal remain = amountToRelease;
        for (FeedBatchLock lock : activeLocks(itemId)) {
            if (remain.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal release = lock.getQuantityKg().min(remain);
            if (release.compareTo(lock.getQuantityKg()) == 0) {
                lock.setStatus(FeedBatchLock.STATUS_RELEASED);
            }
            // 部分释放：缩小该锁占用量
            if (release.compareTo(BigDecimal.ZERO) > 0) {
                if (release.compareTo(lock.getQuantityKg()) < 0) {
                    lock.setQuantityKg(lock.getQuantityKg().subtract(release));
                    lockMapper.updateById(lock);
                } else {
                    lockMapper.updateById(lock);
                }
                batchService.releaseLocked(lock.getFeedBatchId(), release);
                remain = remain.subtract(release);
            }
        }
    }

    private BigDecimal lockedSum(Long itemId) {
        BigDecimal sum = BigDecimal.ZERO;
        for (FeedBatchLock lock : activeLocks(itemId)) {
            sum = sum.add(lock.getQuantityKg());
        }
        return sum;
    }

    private List<FeedBatchLock> activeLocks(Long itemId) {
        return lockMapper.selectList(new QueryWrapper<FeedBatchLock>()
                .eq("order_item_id", itemId)
                .eq("status", FeedBatchLock.STATUS_LOCKED)
                .orderByAsc("id"));
    }

    private List<FeedOrderItem> listItems(Long orderId) {
        return itemMapper.selectList(
                new QueryWrapper<FeedOrderItem>().eq("order_id", orderId).orderByAsc("id"));
    }

    private FeedOrderItem getItemOrThrow(Long orderId, Long itemId) {
        List<FeedOrderItem> items = itemMapper.selectList(new QueryWrapper<FeedOrderItem>()
                .eq("id", itemId).eq("order_id", orderId));
        if (items.isEmpty()) {
            throw new IllegalArgumentException("投喂单id=" + orderId + "下无明细id=" + itemId);
        }
        return items.get(0);
    }

    private SeedlingBatch requireBatchInPond(Long batchId, Long pondId) {
        SeedlingBatch batch = seedlingBatchService.getOrThrow(batchId);
        seedlingBatchService.requireStocked(batch, "准备投喂单");
        if (!batch.getPondId().equals(pondId)) {
            throw new IllegalArgumentException(
                    "批次" + batch.getBatchNo() + "当前不在池塘id=" + pondId + "，请核对或先登记换池");
        }
        Pond pond = pondService.getOrThrow(pondId);
        pondService.requireActive(pond);
        return batch;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private FeedOrder copyOf(FeedOrder source) {
        FeedOrder copy = new FeedOrder();
        copy.setId(source.getId());
        copy.setOrderNo(source.getOrderNo());
        copy.setPondId(source.getPondId());
        copy.setBatchId(source.getBatchId());
        copy.setFeedDate(source.getFeedDate());
        copy.setDayAge(source.getDayAge());
        copy.setWaterTempC(source.getWaterTempC());
        copy.setFeedType(source.getFeedType());
        copy.setFormulaVersionId(source.getFormulaVersionId());
        copy.setStatus(source.getStatus());
        copy.setLockedBy(source.getLockedBy());
        copy.setLockedTime(source.getLockedTime());
        copy.setExecutedBy(source.getExecutedBy());
        copy.setExecutedTime(source.getExecutedTime());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
