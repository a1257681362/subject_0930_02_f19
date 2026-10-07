package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fulfill.dto.HandoverCreateRequest;
import com.evops.aqua.fulfill.dto.HandoverSignRequest;
import com.evops.aqua.fulfill.entity.ColdStore;
import com.evops.aqua.fulfill.entity.DeliveryPoint;
import com.evops.aqua.fulfill.entity.FulfillBox;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.entity.FulfillDelivery;
import com.evops.aqua.fulfill.entity.FulfillHandover;
import com.evops.aqua.fulfill.entity.FulfillOrder;
import com.evops.aqua.fulfill.entity.VarianceRule;
import com.evops.aqua.fulfill.entity.VarianceRuleLine;
import com.evops.aqua.fulfill.mapper.ColdStoreMapper;
import com.evops.aqua.fulfill.mapper.FulfillBoxMapper;
import com.evops.aqua.fulfill.mapper.FulfillDeliveryMapper;
import com.evops.aqua.fulfill.mapper.FulfillHandoverMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 交接与签收：
 * - 交接必须一次交清该子单全部在库箱；按当时生效净重差异规则逐箱判定
 *   （阈值 = 入库净重 × 容忍% + 绝对容差），超差箱 FROZEN 冻结在库不出库，正常箱出库扣冷库重量；
 * - 交接时冻结收货点快照与规则版本快照；一子单至多一次交接；
 * - 签收首签有效（客户已知悉正常箱签收结果与差异/冻结清单），签收后子单 DONE，全部子单签收订单完成。
 */
@Service
public class FulfillHandoverService {

    private final FulfillHandoverMapper handoverMapper;
    private final FulfillBoxMapper boxMapper;
    private final ColdStoreMapper storeMapper;
    private final FulfillDeliveryMapper deliveryMapper;
    private final FulfillDeliveryService deliveryService;
    private final FulfillOrderService orderService;
    private final DeliveryPointService pointService;
    private final VarianceRuleService ruleService;
    private final FulfillEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public FulfillHandoverService(FulfillHandoverMapper handoverMapper,
                                  FulfillBoxMapper boxMapper,
                                  ColdStoreMapper storeMapper,
                                  FulfillDeliveryMapper deliveryMapper,
                                  FulfillDeliveryService deliveryService,
                                  FulfillOrderService orderService,
                                  DeliveryPointService pointService,
                                  VarianceRuleService ruleService,
                                  FulfillEvidenceService evidenceService,
                                  ObjectMapper objectMapper) {
        this.handoverMapper = handoverMapper;
        this.boxMapper = boxMapper;
        this.storeMapper = storeMapper;
        this.deliveryMapper = deliveryMapper;
        this.deliveryService = deliveryService;
        this.orderService = orderService;
        this.pointService = pointService;
        this.ruleService = ruleService;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }
    @Transactional(rollbackFor = Exception.class)
    public FulfillHandover create(Long deliveryId, HandoverCreateRequest request) {
        FulfillHandover duplicated = handoverMapper.selectOne(new QueryWrapper<FulfillHandover>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FulfillDelivery delivery = deliveryService.getOrThrow(deliveryId);
        orderService.requireOpen(orderService.getOrThrow(delivery.getOrderId()));
        if (deliveryService.hasHandover(deliveryId)) {
            throw new IllegalArgumentException("子单" + delivery.getDeliveryNo() + "已交接，不能重复交接");
        }
        List<FulfillBox> inStore = boxMapper.selectList(new QueryWrapper<FulfillBox>()
                .eq("delivery_id", deliveryId).eq("status", FulfillBox.STATUS_IN_STORE).orderByAsc("id"));
        if (inStore.isEmpty()) {
            throw new IllegalArgumentException("子单" + delivery.getDeliveryNo() + "没有在库箱，不能交接");
        }

        // 请求箱集合必须恰好等于该子单全部在库箱，逐箱校验归属与交接净重
        Map<Long, BigDecimal> submittedNet = new HashMap<>();
        for (HandoverCreateRequest.HandoverBox boxReq : request.getBoxes()) {
            if (boxReq.getHandoverNetKg() == null || boxReq.getHandoverNetKg().signum() <= 0) {
                throw new IllegalArgumentException("箱id=" + boxReq.getBoxId() + "的交接净重必须大于0");
            }
            if (submittedNet.put(boxReq.getBoxId(), boxReq.getHandoverNetKg()) != null) {
                throw new IllegalArgumentException("箱id=" + boxReq.getBoxId() + "在交接清单中重复");
            }
        }
        for (FulfillBox box : inStore) {
            if (!submittedNet.containsKey(box.getId())) {
                throw new IllegalArgumentException("交接清单不完整：箱" + box.getBoxNo()
                        + "仍在库，必须一次交清全部在库箱");
            }
        }
        if (submittedNet.size() != inStore.size()) {
            throw new IllegalArgumentException("交接清单包含不属于本子单在库箱的记录：在库"
                    + inStore.size() + "箱，提交" + submittedNet.size() + "箱");
        }

        // 冻结当时生效规则；该规格无规则行直接拒绝（规则缺失不能默认零容差放行）
        VarianceRule rule = ruleService.activeRule();
        VarianceRuleLine ruleLine = ruleService.lineOfSpec(rule.getId(), delivery.getSpecCode());
        if (ruleLine == null) {
            throw new IllegalArgumentException("净重差异规则" + rule.getRuleNo()
                    + "未配置规格" + delivery.getSpecCode() + "的容差，不能交接");
        }

        BigDecimal hundred = new BigDecimal("100");
        BigDecimal totalIntake = BigDecimal.ZERO;
        BigDecimal totalHandover = BigDecimal.ZERO;
        BigDecimal totalDiffAbs = BigDecimal.ZERO;
        int handedCount = 0;
        int frozenCount = 0;
        List<String> frozenNos = new ArrayList<>();
        // 正常箱按入库净重从对应冷库出库，按冷库分组一次性扣减
        Map<Long, BigDecimal> deductByStore = new HashMap<>();

        for (FulfillBox box : inStore) {
            BigDecimal handoverNet = submittedNet.get(box.getId()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal diff = handoverNet.subtract(box.getNetKg());
            BigDecimal diffAbs = diff.abs().setScale(2, RoundingMode.HALF_UP);
            BigDecimal threshold = box.getNetKg()
                    .multiply(ruleLine.getTolerancePct())
                    .divide(hundred, 3, RoundingMode.HALF_UP)
                    .add(ruleLine.getToleranceAbsKg());
            boolean over = diffAbs.compareTo(threshold) > 0;

            FulfillBox before = copyBox(box);
            box.setHandoverNetKg(handoverNet);
            box.setNetDiffKg(diff.setScale(2, RoundingMode.HALF_UP));
            box.setOverTolerance(over);
            totalIntake = totalIntake.add(box.getNetKg());
            totalHandover = totalHandover.add(handoverNet);
            totalDiffAbs = totalDiffAbs.add(diffAbs);

            if (over) {
                box.setStatus(FulfillBox.STATUS_FROZEN);
                frozenCount++;
                frozenNos.add(box.getBoxNo());
            } else {
                box.setStatus(FulfillBox.STATUS_HANDED_OVER);
                handedCount++;
                deductByStore.merge(box.getColdStoreId(), box.getNetKg(), BigDecimal::add);
            }
            boxMapper.updateById(box);
            evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_BOX, box.getId(),
                    over ? FulfillChangeEvidence.ACTION_BOX_FREEZE : FulfillChangeEvidence.ACTION_BOX_HANDOVER,
                    request.getRequestId(), request.getHandoverBy(),
                    before, box, over
                            ? ("箱" + box.getBoxNo() + "交接净重" + handoverNet + "kg 与入库净重"
                            + box.getNetKg() + "kg 差异" + diffAbs + "kg 超过阈值" + threshold
                            + "kg，冻结该箱在库不出库")
                            : ("箱" + box.getBoxNo() + "交接净重" + handoverNet + "kg，差异"
                            + diffAbs + "kg 在阈值" + threshold + "kg 内，正常交接出库"));
        }

        for (Map.Entry<Long, BigDecimal> entry : deductByStore.entrySet()) {
            ColdStore store = storeMapper.selectById(entry.getKey());
            ColdStore before = copyStore(store);
            store.setStoredKg(store.getStoredKg().subtract(entry.getValue()).setScale(2, RoundingMode.HALF_UP));
            storeMapper.updateById(store);
            evidenceService.record(FulfillChangeEvidence.ENTITY_COLD_STORE, store.getId(),
                    FulfillChangeEvidence.ACTION_BOX_HANDOVER, request.getRequestId(), request.getHandoverBy(),
                    before, store, "正常交接箱出库，扣减在库重量" + entry.getValue() + "kg（冻结箱重量不扣减）");
        }

        DeliveryPoint point = pointService.activePointOrThrow(delivery.getDeliveryPointId());
        FulfillHandover handover = new FulfillHandover();
        handover.setHandoverNo(nextHandoverNo());
        handover.setDeliveryId(deliveryId);
        handover.setOrderId(delivery.getOrderId());
        handover.setPointSnapshot(toJson(pointSnapshot(point)));
        handover.setVarianceRuleId(rule.getId());
        handover.setRuleSnapshot(toJson(ruleService.snapshotOf(rule)));
        handover.setTotalIntakeNetKg(totalIntake.setScale(2, RoundingMode.HALF_UP));
        handover.setTotalHandoverNetKg(totalHandover.setScale(2, RoundingMode.HALF_UP));
        handover.setTotalDiffKg(totalDiffAbs.setScale(2, RoundingMode.HALF_UP));
        handover.setHandedBoxCount(handedCount);
        handover.setFrozenBoxCount(frozenCount);
        handover.setFrozenBoxNos(String.join(",", frozenNos));
        handover.setHandoverBy(request.getHandoverBy());
        handover.setHandoverTime(LocalDateTime.now());
        handover.setStatus(FulfillHandover.STATUS_HANDED);
        handover.setRequestId(request.getRequestId());
        handoverMapper.insert(handover);
        // 回填箱的交接单id（冻结箱与正常箱都挂接，便于逐箱追溯）
        for (FulfillBox box : inStore) {
            box.setHandoverId(handover.getId());
            boxMapper.updateById(box);
        }

        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_HANDOVER, handover.getId(),
                FulfillChangeEvidence.ACTION_HANDOVER_CREATE, request.getRequestId(), request.getHandoverBy(),
                null, handover, "子单" + delivery.getDeliveryNo() + "交接：正常" + handedCount
                        + "箱、冻结" + frozenCount + "箱，入库净重合计" + handover.getTotalIntakeNetKg()
                        + "kg、交接净重合计" + handover.getTotalHandoverNetKg() + "kg、差异绝对值合计"
                        + handover.getTotalDiffKg() + "kg，冻结规则" + rule.getRuleNo() + "快照");
        return handover;
    }

    /**
     * 客户签收：仅 HANDED 可签；首签有效，重复签收返回首次回执不覆盖。
     * 签收后子单 DONE；全部子单签收后订单完成。
     */
    @Transactional(rollbackFor = Exception.class)
    public FulfillHandover sign(Long handoverId, HandoverSignRequest request) {
        FulfillHandover handover = getOrThrow(handoverId);
        if (FulfillHandover.STATUS_SIGNED.equals(handover.getStatus())) {
            return handover;
        }
        List<FulfillChangeEvidence> exists = evidenceService.list(
                FulfillChangeEvidence.ENTITY_FULFILL_HANDOVER, handoverId, request.getRequestId());
        if (!exists.isEmpty()) {
            return handover;
        }

        FulfillHandover before = copyHandover(handover);
        handover.setStatus(FulfillHandover.STATUS_SIGNED);
        handover.setSignBy(request.getSignBy());
        handover.setSignTime(LocalDateTime.now());
        handover.setSignRemark(request.getSignRemark());
        handoverMapper.updateById(handover);

        FulfillDelivery delivery = deliveryService.getOrThrow(handover.getDeliveryId());
        delivery.setStatus(FulfillDelivery.STATUS_DONE);
        deliveryMapper.updateById(delivery);

        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_HANDOVER, handover.getId(),
                FulfillChangeEvidence.ACTION_HANDOVER_SIGN, request.getRequestId(), request.getSignBy(),
                before, handover, "客户签收交接单" + handover.getHandoverNo()
                        + "：正常" + handover.getHandedBoxCount() + "箱已收、冻结"
                        + handover.getFrozenBoxCount() + "箱差异已知悉"
                        + (request.getSignRemark() == null ? "" : "，备注：" + request.getSignRemark()));
        orderService.completeIfAllDone(handover.getOrderId(), request.getSignBy(), request.getRequestId());
        return handover;
    }

    public FulfillHandover getOrThrow(Long id) {
        FulfillHandover handover = handoverMapper.selectById(id);
        if (handover == null) {
            throw new IllegalArgumentException("交接单不存在，id=" + id);
        }
        return handover;
    }

    public List<FulfillHandover> list(Long orderId) {
        QueryWrapper<FulfillHandover> wrapper = new QueryWrapper<>();
        wrapper.eq(orderId != null, "order_id", orderId);
        wrapper.orderByAsc("id");
        return handoverMapper.selectList(wrapper);
    }

    private Map<String, Object> pointSnapshot(DeliveryPoint point) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pointId", point.getId());
        snapshot.put("pointNo", point.getPointNo());
        snapshot.put("pointName", point.getPointName());
        snapshot.put("address", point.getAddress());
        snapshot.put("contactPerson", point.getContactPerson());
        snapshot.put("contactPhone", point.getContactPhone());
        return snapshot;
    }

    private String nextHandoverNo() {
        Long count = handoverMapper.selectCount(new QueryWrapper<>());
        return "FH-" + (count + 1);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private FulfillBox copyBox(FulfillBox box) {
        FulfillBox copy = new FulfillBox();
        org.springframework.beans.BeanUtils.copyProperties(box, copy);
        return copy;
    }

    private ColdStore copyStore(ColdStore store) {
        ColdStore copy = new ColdStore();
        org.springframework.beans.BeanUtils.copyProperties(store, copy);
        return copy;
    }

    private FulfillHandover copyHandover(FulfillHandover handover) {
        FulfillHandover copy = new FulfillHandover();
        org.springframework.beans.BeanUtils.copyProperties(handover, copy);
        return copy;
    }
}
