package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.OrderCreateRequest;
import com.evops.aqua.fishing.dto.OrderLineRequest;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingGradingRule;
import com.evops.aqua.fishing.entity.FishingGradingRuleLine;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.mapper.FishingOrderMapper;
import com.evops.aqua.fishing.mapper.FishingReservationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户订单：创建时按「规格+重量区间+交付时间」拆分为捕捞预约，一行一条。
 * 全部预约完成后订单完成，并冻结当时生效的分级规则快照——
 * 已完成订单保留当时分级规则，后续新版本不影响。
 */
@Service
public class FishingOrderService {

    private final FishingOrderMapper orderMapper;
    private final FishingReservationMapper reservationMapper;
    private final FishingGradingRuleService ruleService;
    private final FishingEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public FishingOrderService(FishingOrderMapper orderMapper,
                               FishingReservationMapper reservationMapper,
                               FishingGradingRuleService ruleService,
                               FishingEvidenceService evidenceService,
                               ObjectMapper objectMapper) {
        this.orderMapper = orderMapper;
        this.reservationMapper = reservationMapper;
        this.ruleService = ruleService;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建订单并拆分预约：每行（规格+重量区间+交付时间）生成一条预约。
     * 重量区间必须落在当前生效分级规则对应该规格的区间内。
     * requestId 幂等：补传/重试返回首次订单，不会拆出第二批预约。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingOrder create(OrderCreateRequest request) {
        FishingOrder duplicated = orderMapper.selectOne(new QueryWrapper<FishingOrder>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FishingGradingRule active = ruleService.activeRule();
        List<String> lineKeys = new ArrayList<>();
        for (OrderLineRequest line : request.getLines()) {
            FishingGradingRuleLine ruleLine = ruleService.lineOfSpec(active.getId(), line.getSpecCode());
            if (ruleLine == null) {
                throw new IllegalArgumentException("规格" + line.getSpecCode()
                        + "不在当前生效分级规则" + active.getRuleNo() + "中");
            }
            if (line.getWeightMinG().compareTo(line.getWeightMaxG()) >= 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "的重量区间下限必须小于上限");
            }
            if (line.getWeightMinG().compareTo(ruleLine.getMinWeightG()) < 0
                    || line.getWeightMaxG().compareTo(ruleLine.getMaxWeightG()) > 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "的重量区间["
                        + line.getWeightMinG() + "," + line.getWeightMaxG() + ")超出分级规则区间["
                        + ruleLine.getMinWeightG() + "," + ruleLine.getMaxWeightG() + ")");
            }
            if (line.getPlannedQtyKg() == null || line.getPlannedQtyKg().signum() <= 0) {
                throw new IllegalArgumentException("规格" + line.getSpecCode() + "的计划量必须大于0");
            }
            String key = line.getSpecCode() + "|" + line.getWeightMinG() + "|" + line.getWeightMaxG()
                    + "|" + line.getDeliveryTime();
            if (lineKeys.contains(key)) {
                throw new IllegalArgumentException("订单内存在重复的「规格+重量区间+交付时间」行：" + key);
            }
            lineKeys.add(key);
        }

        FishingOrder order = new FishingOrder();
        order.setOrderNo(nextOrderNo());
        order.setCustomerName(request.getCustomerName());
        order.setStatus(FishingOrder.STATUS_OPEN);
        order.setOperator(request.getOperator());
        order.setRequestId(request.getRequestId());
        order.setRemark(request.getRemark());
        orderMapper.insert(order);

        int seq = 0;
        for (OrderLineRequest line : request.getLines()) {
            seq++;
            FishingReservation reservation = new FishingReservation();
            reservation.setResvNo(order.getOrderNo() + "-R" + seq);
            reservation.setOrderId(order.getId());
            reservation.setSpecCode(line.getSpecCode());
            reservation.setWeightMinG(line.getWeightMinG());
            reservation.setWeightMaxG(line.getWeightMaxG());
            reservation.setDeliveryTime(line.getDeliveryTime());
            reservation.setPlannedQtyKg(line.getPlannedQtyKg());
            reservation.setActualQtyKg(BigDecimal.ZERO);
            reservation.setVehicleNo(request.getVehicleNo());
            reservation.setStatus(FishingReservation.STATUS_DRAFT);
            // 预约请求号按「订单请求号#行序」派生，保证全局唯一且可追溯到原始补传请求
            reservation.setRequestId(request.getRequestId() + "#" + seq);
            reservationMapper.insert(reservation);
            evidenceService.record(FishingChangeEvidence.ENTITY_RESERVATION, reservation.getId(),
                    FishingChangeEvidence.ACTION_RESV_CREATE, reservation.getRequestId(), request.getOperator(),
                    null, reservation, "订单" + order.getOrderNo() + "拆分预约：规格" + line.getSpecCode()
                            + "，重量区间[" + line.getWeightMinG() + "," + line.getWeightMaxG() + ")g，交付"
                            + line.getDeliveryTime() + "，计划" + line.getPlannedQtyKg() + "kg");
        }
        evidenceService.record(FishingChangeEvidence.ENTITY_ORDER, order.getId(),
                FishingChangeEvidence.ACTION_ORDER_CREATE, request.getRequestId(), request.getOperator(),
                null, order, "创建客户订单" + order.getOrderNo() + "（" + request.getCustomerName()
                        + "），拆分预约" + seq + "条");
        return order;
    }

    /**
     * 订单视图：含拆分出的预约；已完成订单附当时冻结的分级规则快照。
     */
    public Map<String, Object> view(Long id) {
        FishingOrder order = getOrThrow(id);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", order.getId());
        view.put("orderNo", order.getOrderNo());
        view.put("customerName", order.getCustomerName());
        view.put("status", order.getStatus());
        view.put("completedTime", order.getCompletedTime());
        view.put("reservations", reservationsOf(id));
        if (FishingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            view.put("gradingRuleId", order.getGradingRuleId());
            view.put("gradingRuleSnapshot", parseJson(order.getGradingRuleSnapshot()));
        }
        return view;
    }

    /**
     * 订单完成：冻结当时生效的分级规则快照（版本号 + 全部规格区间）。
     * 由预约完成逻辑在「全部预约完成」时触发；已完成订单重复触发幂等返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingOrder completeIfAllDone(Long orderId, String operator, String requestId) {
        FishingOrder order = getOrThrow(orderId);
        if (FishingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            return order;
        }
        List<FishingReservation> reservations = reservationsOf(orderId);
        boolean allDone = !reservations.isEmpty() && reservations.stream()
                .allMatch(r -> FishingReservation.STATUS_COMPLETED.equals(r.getStatus()));
        if (!allDone) {
            return order;
        }
        FishingGradingRule active = ruleService.activeRule();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("ruleNo", active.getRuleNo());
        snapshot.put("versionNo", active.getVersionNo());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (FishingGradingRuleLine line : ruleService.linesOf(active.getId())) {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("specCode", line.getSpecCode());
            l.put("minWeightG", line.getMinWeightG());
            l.put("maxWeightG", line.getMaxWeightG());
            lines.add(l);
        }
        snapshot.put("lines", lines);

        FishingOrder before = copyOf(order);
        order.setStatus(FishingOrder.STATUS_COMPLETED);
        order.setGradingRuleId(active.getId());
        order.setGradingRuleSnapshot(toJson(snapshot));
        order.setCompletedTime(LocalDateTime.now());
        orderMapper.updateById(order);
        evidenceService.record(FishingChangeEvidence.ENTITY_ORDER, order.getId(),
                FishingChangeEvidence.ACTION_ORDER_COMPLETE, requestId, operator,
                before, order, "全部预约完成，订单完成并冻结当时分级规则" + active.getRuleNo() + "快照");
        return order;
    }

    public FishingOrder getOrThrow(Long id) {
        FishingOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw new IllegalArgumentException("客户订单不存在，id=" + id);
        }
        return order;
    }

    public List<FishingOrder> list(String status) {
        QueryWrapper<FishingOrder> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return orderMapper.selectList(wrapper);
    }

    public List<FishingReservation> reservationsOf(Long orderId) {
        return reservationMapper.selectList(new QueryWrapper<FishingReservation>()
                .eq("order_id", orderId).orderByAsc("id"));
    }

    private String nextOrderNo() {
        Long count = orderMapper.selectCount(new QueryWrapper<>());
        return "FO-" + (count + 1);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return String.valueOf(value);
        }
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

    private FishingOrder copyOf(FishingOrder order) {
        FishingOrder copy = new FishingOrder();
        org.springframework.beans.BeanUtils.copyProperties(order, copy);
        return copy;
    }
}
