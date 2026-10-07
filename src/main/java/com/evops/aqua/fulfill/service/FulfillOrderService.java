package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.mapper.FishingOrderMapper;
import com.evops.aqua.fishing.mapper.FishingReservationMapper;
import com.evops.aqua.fulfill.dto.FulfillOrderCreateRequest;
import com.evops.aqua.fulfill.dto.FulfillOrderLineRequest;
import com.evops.aqua.fulfill.entity.DeliveryPoint;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.entity.FulfillDelivery;
import com.evops.aqua.fulfill.entity.FulfillHandover;
import com.evops.aqua.fulfill.entity.FulfillOrder;
import com.evops.aqua.fulfill.entity.FulfillOutbox;
import com.evops.aqua.fulfill.mapper.DeliveryPointMapper;
import com.evops.aqua.fulfill.mapper.FulfillBoxMapper;
import com.evops.aqua.fulfill.mapper.FulfillDeliveryMapper;
import com.evops.aqua.fulfill.mapper.FulfillHandoverMapper;
import com.evops.aqua.fulfill.mapper.FulfillOrderMapper;
import com.evops.aqua.fulfill.mapper.FulfillOutboxMapper;
import com.evops.aqua.fulfill.entity.FulfillBox;
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
 * 履约订单：
 * - 仅可基于已完成的捕捞订单创建（货已真实存在），一张捕捞订单至多一张履约订单；
 * - 创建即按「规格 × 收货点」拆出履约子单，行金额与订单总额由服务端按 数量×单价 重算，
 *   之后再拆/改点均不改变 total_amount 口径；
 * - 创建事务只落订单（PENDING_SYNC）、子单与 PENDING 发件箱；外部同步由 Controller 事务外触发，
 *   外部无响应时订单停留 PENDING_SYNC，任何履约动作一律禁止。
 */
@Service
public class FulfillOrderService {

    private final FulfillOrderMapper orderMapper;
    private final FulfillDeliveryMapper deliveryMapper;
    private final FishingOrderMapper fishingOrderMapper;
    private final FishingReservationMapper fishingReservationMapper;
    private final DeliveryPointService pointService;
    private final FulfillEvidenceService evidenceService;
    private final FulfillOutboxMapper outboxMapper;
    private final FulfillBoxMapper boxMapper;
    private final FulfillHandoverMapper handoverMapper;
    private final DeliveryPointMapper pointMapper;
    private final ObjectMapper objectMapper;

    public static final String ROLE_INTERNAL = "INTERNAL";

    public FulfillOrderService(FulfillOrderMapper orderMapper,
                               FulfillDeliveryMapper deliveryMapper,
                               FishingOrderMapper fishingOrderMapper,
                               FishingReservationMapper fishingReservationMapper,
                               DeliveryPointService pointService,
                               FulfillEvidenceService evidenceService,
                               FulfillOutboxMapper outboxMapper,
                               FulfillBoxMapper boxMapper,
                               FulfillHandoverMapper handoverMapper,
                               DeliveryPointMapper pointMapper,
                               ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.deliveryMapper = deliveryMapper;
        this.fishingOrderMapper = fishingOrderMapper;
        this.fishingReservationMapper = fishingReservationMapper;
        this.pointService = pointService;
        this.evidenceService = evidenceService;
        this.outboxMapper = outboxMapper;
        this.boxMapper = boxMapper;
        this.handoverMapper = handoverMapper;
        this.pointMapper = pointMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 建单并按规格×收货点拆子单。本方法只落库（含 PENDING 发件箱），不发起外部调用。
     * requestId 幂等：重复请求（含外部 DOWN 后的重试）返回首次订单，绝不拆出第二批子单。
     */
    @Transactional(rollbackFor = Exception.class)
    public FulfillOrder create(FulfillOrderCreateRequest request) {
        FulfillOrder duplicated = orderMapper.selectOne(new QueryWrapper<FulfillOrder>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FishingOrder fishingOrder = fishingOrderMapper.selectById(request.getFishingOrderId());
        if (fishingOrder == null) {
            throw new IllegalArgumentException("来源捕捞订单不存在，id=" + request.getFishingOrderId());
        }
        if (!FishingOrder.STATUS_COMPLETED.equals(fishingOrder.getStatus())) {
            throw new IllegalArgumentException("来源捕捞订单" + fishingOrder.getOrderNo()
                    + "尚未完成（当前" + fishingOrder.getStatus() + "），不能创建履约单");
        }
        FulfillOrder sameSource = orderMapper.selectOne(new QueryWrapper<FulfillOrder>()
                .eq("fishing_order_id", request.getFishingOrderId()).last("limit 1"));
        if (sameSource != null) {
            throw new IllegalArgumentException("捕捞订单" + fishingOrder.getOrderNo()
                    + "已存在履约订单" + sameSource.getOrderNo() + "，不能重复履约");
        }

        // 逐行校验：归属捕捞订单、规格一致、收货点有效、数量为正、按预约汇总不超实际捕捞量、单内口径不重复
        List<String> lineKeys = new ArrayList<>();
        Map<Long, BigDecimal> qtyByReservation = new HashMap<>();
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (FulfillOrderLineRequest line : request.getLines()) {
            FishingReservation reservation = fishingReservationMapper.selectById(line.getReservationId());
            if (reservation == null || !fishingOrder.getId().equals(reservation.getOrderId())) {
                throw new IllegalArgumentException("来源捕捞预约不存在或不属于捕捞订单"
                        + fishingOrder.getOrderNo() + "，reservationId=" + line.getReservationId());
            }
            if (!FishingReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
                throw new IllegalArgumentException("捕捞预约" + reservation.getResvNo()
                        + "尚未完成，不能用于履约拆单");
            }
            if (!reservation.getSpecCode().equals(line.getSpecCode())) {
                throw new IllegalArgumentException("行规格" + line.getSpecCode()
                        + "与来源预约" + reservation.getResvNo() + "的规格"
                        + reservation.getSpecCode() + "不一致");
            }
            pointService.activePointOrThrow(line.getDeliveryPointId());
            if (line.getPlannedNetKg() == null || line.getPlannedNetKg().signum() <= 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "的计划净重必须大于0");
            }
            if (line.getUnitPrice() == null || line.getUnitPrice().signum() <= 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "的协议单价必须大于0");
            }
            BigDecimal summed = qtyByReservation.getOrDefault(reservation.getId(), BigDecimal.ZERO)
                    .add(line.getPlannedNetKg());
            if (summed.compareTo(reservation.getActualQtyKg()) > 0) {
                throw new IllegalArgumentException("预约" + reservation.getResvNo()
                        + "各履约行计划净重合计" + summed + "kg 超过实际捕捞量"
                        + reservation.getActualQtyKg() + "kg");
            }
            qtyByReservation.put(reservation.getId(), summed);
            String key = line.getReservationId() + "|" + line.getSpecCode() + "|" + line.getDeliveryPointId();
            if (lineKeys.contains(key)) {
                throw new IllegalArgumentException("订单内存在重复的「预约+规格+收货点」行：" + key);
            }
            lineKeys.add(key);
            totalAmount = totalAmount.add(line.getPlannedNetKg().multiply(line.getUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP));
        }

        FulfillOrder order = new FulfillOrder();
        order.setOrderNo(nextOrderNo());
        order.setFishingOrderId(fishingOrder.getId());
        order.setCustomerName(fishingOrder.getCustomerName());
        order.setTotalAmount(totalAmount);
        order.setStatus(FulfillOrder.STATUS_PENDING_SYNC);
        order.setOperator(request.getOperator());
        order.setRequestId(request.getRequestId());
        order.setRemark(request.getRemark());
        orderMapper.insert(order);

        List<Map<String, Object>> snapshotLines = new ArrayList<>();
        List<Map<String, Object>> payloadLines = new ArrayList<>();
        int seq = 0;
        for (FulfillOrderLineRequest line : request.getLines()) {
            seq++;
            BigDecimal lineAmount = line.getPlannedNetKg().multiply(line.getUnitPrice())
                    .setScale(2, RoundingMode.HALF_UP);
            FulfillDelivery delivery = new FulfillDelivery();
            delivery.setDeliveryNo(order.getOrderNo() + "-D" + seq);
            delivery.setOrderId(order.getId());
            delivery.setSourceReservationId(line.getReservationId());
            delivery.setSpecCode(line.getSpecCode());
            delivery.setDeliveryPointId(line.getDeliveryPointId());
            delivery.setPlannedNetKg(line.getPlannedNetKg());
            delivery.setUnitPrice(line.getUnitPrice());
            delivery.setLineAmount(lineAmount);
            delivery.setIntakeNetKg(BigDecimal.ZERO);
            delivery.setStatus(FulfillDelivery.STATUS_PLANNED);
            delivery.setRequestId(request.getRequestId() + "#" + seq);
            deliveryMapper.insert(delivery);
            evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_DELIVERY, delivery.getId(),
                    FulfillChangeEvidence.ACTION_DELIVERY_CREATE, delivery.getRequestId(), request.getOperator(),
                    null, delivery, "履约单" + order.getOrderNo() + "按规格" + line.getSpecCode()
                            + "+收货点" + line.getDeliveryPointId() + "拆出子单" + delivery.getDeliveryNo()
                            + "，计划净重" + line.getPlannedNetKg() + "kg");

            Map<String, Object> snap = new LinkedHashMap<>();
            snap.put("deliveryNo", delivery.getDeliveryNo());
            snap.put("reservationId", line.getReservationId());
            snap.put("specCode", line.getSpecCode());
            snap.put("deliveryPointId", line.getDeliveryPointId());
            snap.put("plannedNetKg", line.getPlannedNetKg());
            snap.put("unitPrice", line.getUnitPrice());
            snap.put("lineAmount", lineAmount);
            snapshotLines.add(snap);

            // 外发载荷只含履约事实，不含单价/金额等内部口径
            Map<String, Object> payloadLine = new LinkedHashMap<>();
            payloadLine.put("deliveryNo", delivery.getDeliveryNo());
            payloadLine.put("reservationId", line.getReservationId());
            payloadLine.put("specCode", line.getSpecCode());
            payloadLine.put("deliveryPointId", line.getDeliveryPointId());
            payloadLine.put("plannedNetKg", line.getPlannedNetKg());
            payloadLines.add(payloadLine);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("orderNo", order.getOrderNo());
        snapshot.put("fishingOrderNo", fishingOrder.getOrderNo());
        snapshot.put("totalAmount", totalAmount);
        snapshot.put("lines", snapshotLines);
        order.setAmountSnapshot(toJson(snapshot));
        orderMapper.updateById(order);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderNo", order.getOrderNo());
        payload.put("fishingOrderNo", fishingOrder.getOrderNo());
        payload.put("customerName", fishingOrder.getCustomerName());
        payload.put("lines", payloadLines);
        FulfillOutbox outbox = new FulfillOutbox();
        outbox.setEventId(FulfillOutbox.EVENT_PREFIX + request.getRequestId());
        outbox.setBizType(FulfillOutbox.BIZ_SPLIT);
        outbox.setBizId(order.getId());
        outbox.setPayload(toJson(payload));
        outbox.setStatus(FulfillOutbox.STATUS_PENDING);
        outbox.setAttempts(0);
        outbox.setRequestId(request.getRequestId());
        outboxMapper.insert(outbox);

        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_ORDER, order.getId(),
                FulfillChangeEvidence.ACTION_ORDER_CREATE, request.getRequestId(), request.getOperator(),
                null, order, "创建履约订单" + order.getOrderNo() + "（来源捕捞订单"
                        + fishingOrder.getOrderNo() + "），拆出子单" + seq + "条，金额口径合计"
                        + totalAmount + "元，待海水养殖管理确认");
        return order;
    }

    /**
     * 订单可履约门：PENDING_SYNC（外部未确认）与 COMPLETED 一律禁止入库/交接/再拆/改点。
     */
    public FulfillOrder requireOpen(FulfillOrder order) {
        if (FulfillOrder.STATUS_PENDING_SYNC.equals(order.getStatus())) {
            throw new IllegalArgumentException("拆单尚未经海水养殖管理确认（PENDING_SYNC），禁止入库/交接/签收/再拆/改点");
        }
        if (FulfillOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            throw new IllegalArgumentException("履约订单" + order.getOrderNo() + "已完成，不能再变更");
        }
        return order;
    }

    /**
     * 全部子单签收后订单完成；由签收逻辑触发，重复触发/未全部完成均原样返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public FulfillOrder completeIfAllDone(Long orderId, String operator, String requestId) {
        FulfillOrder order = getOrThrow(orderId);
        if (!FulfillOrder.STATUS_OPEN.equals(order.getStatus())) {
            return order;
        }
        List<FulfillDelivery> deliveries = deliveriesOf(orderId);
        boolean allDone = !deliveries.isEmpty() && deliveries.stream()
                .allMatch(d -> FulfillDelivery.STATUS_DONE.equals(d.getStatus()));
        if (!allDone) {
            return order;
        }
        FulfillOrder before = copyOf(order);
        order.setStatus(FulfillOrder.STATUS_COMPLETED);
        order.setCompletedTime(LocalDateTime.now());
        orderMapper.updateById(order);
        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_ORDER, order.getId(),
                FulfillChangeEvidence.ACTION_ORDER_COMPLETE, requestId, operator,
                before, order, "全部子单签收完成，履约订单" + order.getOrderNo() + "完成");
        return order;
    }

    public FulfillOrder getOrThrow(Long id) {
        FulfillOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("履约订单不存在，id=" + id);
        }
        return order;
    }

    /**
     * 按幂等请求号查首次订单：补传/重试（含外部 DOWN 期间）命中即直接返回，绝不再次通知外部。
     */
    public FulfillOrder findByRequestId(String requestId) {
        return orderMapper.selectOne(new QueryWrapper<FulfillOrder>()
                .eq("request_id", requestId).last("limit 1"));
    }

    public List<FulfillOrder> list(String status) {
        QueryWrapper<FulfillOrder> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return orderMapper.selectList(wrapper);
    }

    /**
     * 订单视图：
     * - INTERNAL：全量字段（含单价/行金额/订单总额/入库温度/毛重皮重/冷库等内部口径）；
     * - CUSTOMER（缺省/未知角色的最小权限默认）：白名单 Map，只含订单状态、子单规格/计划净重/收货点名、
     *   箱号/入库净重/交接净重/差异/是否超差、签收人与签收时间/备注/汇总差异/冻结箱数——
     *   键名与值中不出现任何单价/金额/成本/温度/冷库信息。
     */
    public Map<String, Object> view(Long id, String role) {
        FulfillOrder order = getOrThrow(id);
        boolean internal = ROLE_INTERNAL.equalsIgnoreCase(role);

        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderNo", order.getOrderNo());
        view.put("customerName", order.getCustomerName());
        view.put("status", order.getStatus());
        view.put("completedTime", order.getCompletedTime());
        if (internal) {
            view.put("id", order.getId());
            view.put("fishingOrderId", order.getFishingOrderId());
            view.put("totalAmount", order.getTotalAmount());
            view.put("amountSnapshot", parseJson(order.getAmountSnapshot()));
            view.put("syncedTime", order.getSyncedTime());
            view.put("operator", order.getOperator());
            view.put("remark", order.getRemark());
        }

        List<Map<String, Object>> deliveryViews = new ArrayList<>();
        for (FulfillDelivery delivery : deliveriesOf(id)) {
            Map<String, Object> dv = new LinkedHashMap<>();
            dv.put("deliveryNo", delivery.getDeliveryNo());
            dv.put("specCode", delivery.getSpecCode());
            dv.put("plannedNetKg", delivery.getPlannedNetKg());
            dv.put("deliveryPointName", pointName(delivery.getDeliveryPointId()));
            dv.put("status", delivery.getStatus());
            if (internal) {
                dv.put("id", delivery.getId());
                dv.put("deliveryPointId", delivery.getDeliveryPointId());
                dv.put("unitPrice", delivery.getUnitPrice());
                dv.put("lineAmount", delivery.getLineAmount());
                dv.put("intakeNetKg", delivery.getIntakeNetKg());
            }

            List<Map<String, Object>> boxViews = new ArrayList<>();
            for (FulfillBox box : boxesOf(delivery.getId())) {
                Map<String, Object> bv = new LinkedHashMap<>();
                bv.put("boxNo", box.getBoxNo());
                bv.put("intakeNetKg", box.getNetKg());
                bv.put("handoverNetKg", box.getHandoverNetKg());
                bv.put("netDiffKg", box.getNetDiffKg());
                bv.put("overTolerance", box.getOverTolerance());
                bv.put("boxStatus", box.getStatus());
                if (internal) {
                    bv.put("id", box.getId());
                    bv.put("coldStoreId", box.getColdStoreId());
                    bv.put("intakeTemp", box.getIntakeTemp());
                    bv.put("grossKg", box.getGrossKg());
                    bv.put("tareKg", box.getTareKg());
                }
                boxViews.add(bv);
            }
            dv.put("boxes", boxViews);

            FulfillHandover handover = handoverMapper.selectOne(new QueryWrapper<FulfillHandover>()
                    .eq("delivery_id", delivery.getId()).last("limit 1"));
            if (handover != null) {
                Map<String, Object> hv = new LinkedHashMap<>();
                hv.put("handoverNo", handover.getHandoverNo());
                hv.put("status", handover.getStatus());
                hv.put("totalIntakeNetKg", handover.getTotalIntakeNetKg());
                hv.put("totalHandoverNetKg", handover.getTotalHandoverNetKg());
                hv.put("totalDiffKg", handover.getTotalDiffKg());
                hv.put("handedBoxCount", handover.getHandedBoxCount());
                hv.put("frozenBoxCount", handover.getFrozenBoxCount());
                hv.put("frozenBoxNos", handover.getFrozenBoxNos());
                hv.put("handoverTime", handover.getHandoverTime());
                hv.put("signBy", handover.getSignBy());
                hv.put("signTime", handover.getSignTime());
                hv.put("signRemark", handover.getSignRemark());
                if (internal) {
                    hv.put("id", handover.getId());
                    hv.put("varianceRuleId", handover.getVarianceRuleId());
                    hv.put("pointSnapshot", parseJson(handover.getPointSnapshot()));
                    hv.put("ruleSnapshot", parseJson(handover.getRuleSnapshot()));
                }
                dv.put("handover", hv);
            }
            deliveryViews.add(dv);
        }
        view.put("deliveries", deliveryViews);
        return view;
    }

    private List<FulfillBox> boxesOf(Long deliveryId) {
        return boxMapper.selectList(new QueryWrapper<FulfillBox>()
                .eq("delivery_id", deliveryId).orderByAsc("id"));
    }

    private String pointName(Long pointId) {
        DeliveryPoint point = pointMapper.selectById(pointId);
        return point == null ? null : point.getPointName();
    }

    private Object parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ex) {
            return json;
        }
    }

    public List<FulfillDelivery> deliveriesOf(Long orderId) {
        return deliveryMapper.selectList(new QueryWrapper<FulfillDelivery>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private String nextOrderNo() {
        Long count = orderMapper.selectCount(new QueryWrapper<>());
        return "FUO-" + (count + 1);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
    }

    private FulfillOrder copyOf(FulfillOrder order) {
        FulfillOrder copy = new FulfillOrder();
        org.springframework.beans.BeanUtils.copyProperties(order, copy);
        return copy;
    }
}
