package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.mapper.FishingReservationMapper;
import com.evops.aqua.fulfill.dto.BoxIntakeRequest;
import com.evops.aqua.fulfill.entity.ColdStore;
import com.evops.aqua.fulfill.entity.FulfillBox;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.entity.FulfillDelivery;
import com.evops.aqua.fulfill.entity.FulfillOrder;
import com.evops.aqua.fulfill.mapper.ColdStoreMapper;
import com.evops.aqua.fulfill.mapper.FulfillBoxMapper;
import com.evops.aqua.fulfill.mapper.FulfillDeliveryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 箱入库：捕捞后逐箱进入冷藏库，记录入库温度、箱号与称重结果。
 * 净重一律由服务端按 毛重-皮重 计算（不信任前端净重）；冷库在库重量与子单入库净重同事务累加。
 * requestId 幂等重试只返回首箱，不重复加账；箱号全局唯一。
 */
@Service
public class FulfillBoxService {

    private static final BigDecimal TEMP_MIN = new BigDecimal("-30");
    private static final BigDecimal TEMP_MAX = new BigDecimal("25");

    private final FulfillBoxMapper boxMapper;
    private final ColdStoreMapper storeMapper;
    private final FulfillDeliveryMapper deliveryMapper;
    private final FishingReservationMapper fishingReservationMapper;
    private final FulfillDeliveryService deliveryService;
    private final FulfillOrderService orderService;
    private final ColdStoreService storeService;
    private final FulfillEvidenceService evidenceService;

    public FulfillBoxService(FulfillBoxMapper boxMapper,
                             ColdStoreMapper storeMapper,
                             FulfillDeliveryMapper deliveryMapper,
                             FishingReservationMapper fishingReservationMapper,
                             FulfillDeliveryService deliveryService,
                             FulfillOrderService orderService,
                             ColdStoreService storeService,
                             FulfillEvidenceService evidenceService) {
        this.boxMapper = boxMapper;
        this.storeMapper = storeMapper;
        this.deliveryMapper = deliveryMapper;
        this.fishingReservationMapper = fishingReservationMapper;
        this.deliveryService = deliveryService;
        this.orderService = orderService;
        this.storeService = storeService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public FulfillBox intake(Long deliveryId, BoxIntakeRequest request) {
        FulfillBox duplicated = boxMapper.selectOne(new QueryWrapper<FulfillBox>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FulfillDelivery delivery = deliveryService.getOrThrow(deliveryId);
        FulfillOrder order = orderService.requireOpen(orderService.getOrThrow(delivery.getOrderId()));
        if (deliveryService.hasHandover(deliveryId)) {
            throw new IllegalArgumentException("子单" + delivery.getDeliveryNo() + "已交接，不能再入库");
        }
        FishingReservation reservation = fishingReservationMapper.selectById(delivery.getSourceReservationId());
        if (reservation == null
                || !FishingReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("来源捕捞预约不存在或未完成，不能入库");
        }
        FulfillBox sameNo = boxMapper.selectOne(new QueryWrapper<FulfillBox>()
                .eq("box_no", request.getBoxNo()).last("limit 1"));
        if (sameNo != null) {
            throw new IllegalArgumentException("箱号已存在：" + request.getBoxNo());
        }
        if (request.getIntakeTemp() == null
                || request.getIntakeTemp().compareTo(TEMP_MIN) < 0
                || request.getIntakeTemp().compareTo(TEMP_MAX) > 0) {
            throw new IllegalArgumentException("入库温度必须在[" + TEMP_MIN + "," + TEMP_MAX + "]℃之间");
        }
        if (request.getTareKg() == null || request.getTareKg().signum() < 0) {
            throw new IllegalArgumentException("皮重必须大于等于0");
        }
        if (request.getGrossKg() == null || request.getGrossKg().compareTo(request.getTareKg()) <= 0) {
            throw new IllegalArgumentException("毛重必须大于皮重，净重才能为正");
        }
        BigDecimal net = request.getGrossKg().subtract(request.getTareKg()).setScale(2, RoundingMode.HALF_UP);

        ColdStore store = storeService.enabledStoreOrThrow(request.getColdStoreId());
        storeService.requireCapacity(store, net);

        FulfillBox box = new FulfillBox();
        box.setBoxNo(request.getBoxNo());
        box.setDeliveryId(deliveryId);
        box.setOrderId(order.getId());
        box.setColdStoreId(store.getId());
        box.setReservationId(delivery.getSourceReservationId());
        box.setSpecCode(delivery.getSpecCode());
        box.setIntakeTemp(request.getIntakeTemp());
        box.setGrossKg(request.getGrossKg());
        box.setTareKg(request.getTareKg());
        box.setNetKg(net);
        box.setStatus(FulfillBox.STATUS_IN_STORE);
        box.setWeighedBy(request.getWeighedBy());
        box.setWeighedTime(LocalDateTime.now());
        box.setRequestId(request.getRequestId());
        box.setRemark(request.getRemark());
        boxMapper.insert(box);

        ColdStore storeBefore = copyStore(store);
        store.setStoredKg(store.getStoredKg().add(net).setScale(2, RoundingMode.HALF_UP));
        storeMapper.updateById(store);

        FulfillDelivery deliveryBefore = copyDelivery(delivery);
        delivery.setIntakeNetKg(delivery.getIntakeNetKg().add(net).setScale(2, RoundingMode.HALF_UP));
        if (FulfillDelivery.STATUS_PLANNED.equals(delivery.getStatus())) {
            delivery.setStatus(FulfillDelivery.STATUS_FULFILLING);
        }
        deliveryMapper.updateById(delivery);

        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_BOX, box.getId(),
                FulfillChangeEvidence.ACTION_BOX_INTAKE, request.getRequestId(), request.getWeighedBy(),
                null, box, "箱" + box.getBoxNo() + "入冷藏库" + store.getStoreNo()
                        + "：入库温度" + request.getIntakeTemp() + "℃，毛重" + request.getGrossKg()
                        + "-皮重" + request.getTareKg() + "=净重" + net + "kg");
        evidenceService.record(FulfillChangeEvidence.ENTITY_COLD_STORE, store.getId(),
                FulfillChangeEvidence.ACTION_BOX_INTAKE, request.getRequestId(), request.getWeighedBy(),
                storeBefore, store, "箱" + box.getBoxNo() + "入库，在库重量累加" + net + "kg");
        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_DELIVERY, delivery.getId(),
                FulfillChangeEvidence.ACTION_BOX_INTAKE, request.getRequestId(), request.getWeighedBy(),
                deliveryBefore, delivery, "子单" + delivery.getDeliveryNo() + "入库净重累加" + net + "kg");
        return box;
    }

    public List<FulfillBox> boxesOf(Long deliveryId, String status) {
        QueryWrapper<FulfillBox> wrapper = new QueryWrapper<>();
        wrapper.eq("delivery_id", deliveryId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return boxMapper.selectList(wrapper);
    }

    private ColdStore copyStore(ColdStore store) {
        ColdStore copy = new ColdStore();
        org.springframework.beans.BeanUtils.copyProperties(store, copy);
        return copy;
    }

    private FulfillDelivery copyDelivery(FulfillDelivery delivery) {
        FulfillDelivery copy = new FulfillDelivery();
        org.springframework.beans.BeanUtils.copyProperties(delivery, copy);
        return copy;
    }
}
