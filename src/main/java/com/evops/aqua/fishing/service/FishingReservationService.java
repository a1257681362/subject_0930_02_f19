package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.LoadRecordRequest;
import com.evops.aqua.fishing.dto.ReservationCompleteRequest;
import com.evops.aqua.fishing.dto.ReservationLockRequest;
import com.evops.aqua.fishing.dto.VehicleChangeRequest;
import com.evops.aqua.fishing.entity.FishingBin;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingGradingRule;
import com.evops.aqua.fishing.entity.FishingGradingRuleLine;
import com.evops.aqua.fishing.entity.FishingLoad;
import com.evops.aqua.fishing.entity.FishingNotification;
import com.evops.aqua.fishing.entity.FishingOrder;
import com.evops.aqua.fishing.entity.FishingReservation;
import com.evops.aqua.fishing.entity.FishingVariance;
import com.evops.aqua.fishing.entity.FishingVehicleChange;
import com.evops.aqua.fishing.mapper.FishingBinMapper;
import com.evops.aqua.fishing.mapper.FishingLoadMapper;
import com.evops.aqua.fishing.mapper.FishingNotificationMapper;
import com.evops.aqua.fishing.mapper.FishingReservationMapper;
import com.evops.aqua.fishing.mapper.FishingVarianceMapper;
import com.evops.aqua.fishing.mapper.FishingVehicleChangeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 捕捞预约执行：锁定占用库位（他单不得占用）→ 现场按实际规格分仓
 * （库位状态与待检标识同步）→ 完成核对生成差异单 → 订单完成冻结分级规则。
 * 临时换车登记原因并触发客户通知。补录/重试按 requestId 幂等，只形成一条可用结果。
 */
@Service
public class FishingReservationService {

    private final FishingReservationMapper reservationMapper;
    private final FishingBinMapper binMapper;
    private final FishingLoadMapper loadMapper;
    private final FishingVarianceMapper varianceMapper;
    private final FishingVehicleChangeMapper vehicleChangeMapper;
    private final FishingNotificationMapper notificationMapper;
    private final FishingGradingRuleService ruleService;
    private final FishingOrderService orderService;
    private final FishingEvidenceService evidenceService;

    public FishingReservationService(FishingReservationMapper reservationMapper,
                                     FishingBinMapper binMapper,
                                     FishingLoadMapper loadMapper,
                                     FishingVarianceMapper varianceMapper,
                                     FishingVehicleChangeMapper vehicleChangeMapper,
                                     FishingNotificationMapper notificationMapper,
                                     FishingGradingRuleService ruleService,
                                     FishingOrderService orderService,
                                     FishingEvidenceService evidenceService) {
        this.reservationMapper = reservationMapper;
        this.binMapper = binMapper;
        this.loadMapper = loadMapper;
        this.varianceMapper = varianceMapper;
        this.vehicleChangeMapper = vehicleChangeMapper;
        this.notificationMapper = notificationMapper;
        this.ruleService = ruleService;
        this.orderService = orderService;
        this.evidenceService = evidenceService;
    }

    /**
     * 锁定预约：占用指定库位（缺省按规格自动挑选空闲库位）。
     * 库位已被其他订单占用时拒绝——锁定后不能被其他订单占用。
     * 已锁定预约重复锁定幂等返回当前状态。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingReservation lock(Long id, ReservationLockRequest request) {
        FishingReservation reservation = getOrThrow(id);
        if (FishingReservation.STATUS_LOCKED.equals(reservation.getStatus())
                || FishingReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
            return reservation;
        }
        FishingBin bin = pickBin(reservation, request.getBinId());
        FishingReservation before = copyOf(reservation);
        reservation.setStatus(FishingReservation.STATUS_LOCKED);
        reservation.setBinId(bin.getId());
        if (request.getVehicleNo() != null && !request.getVehicleNo().trim().isEmpty()) {
            reservation.setVehicleNo(request.getVehicleNo());
        }
        reservation.setLockedBy(request.getOperator());
        reservation.setLockedTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);

        FishingBin binBefore = copyOf(bin);
        bin.setOccupiedOrderId(reservation.getOrderId());
        bin.setOccupiedResvId(reservation.getId());
        binMapper.updateById(bin);

        evidenceService.record(FishingChangeEvidence.ENTITY_RESERVATION, reservation.getId(),
                FishingChangeEvidence.ACTION_RESV_LOCK, request.getRequestId(), request.getOperator(),
                before, reservation, "预约" + reservation.getResvNo() + "锁定库位" + bin.getBinNo());
        evidenceService.record(FishingChangeEvidence.ENTITY_BIN, bin.getId(),
                FishingChangeEvidence.ACTION_RESV_LOCK, request.getRequestId(), request.getOperator(),
                binBefore, bin, "库位" + bin.getBinNo() + "被订单id=" + reservation.getOrderId() + "占用");
        return reservation;
    }

    /**
     * 捕捞现场分仓：实际均重按当时生效分级规则判实际规格，入对应规格库位；
     * 库位状态与待检标识同一事务同步更新（装货即待检）。
     * requestId 强制幂等——补录/重试返回首次记录，不重复加账，只形成一条可用结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingLoad recordLoad(Long id, LoadRecordRequest request) {
        FishingLoad duplicated = loadMapper.selectOne(new QueryWrapper<FishingLoad>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FishingReservation reservation = getOrThrow(id);
        if (!FishingReservation.STATUS_LOCKED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("仅已锁定预约可登记分仓，当前状态：" + reservation.getStatus());
        }
        if (request.getWeightKg().signum() <= 0) {
            throw new IllegalArgumentException("入仓重量必须大于0");
        }
        if (request.getActualAvgWeightG().signum() <= 0) {
            throw new IllegalArgumentException("实际均重必须大于0");
        }
        FishingGradingRule active = ruleService.activeRule();
        FishingGradingRuleLine graded = ruleService.grade(request.getActualAvgWeightG());
        FishingBin bin = routeBin(reservation, graded.getSpecCode(), request.getWeightKg());

        FishingLoad load = new FishingLoad();
        load.setLoadNo(nextLoadNo(reservation.getId()));
        load.setReservationId(reservation.getId());
        load.setOrderId(reservation.getOrderId());
        load.setBinId(bin.getId());
        load.setSpecCode(graded.getSpecCode());
        load.setActualAvgWeightG(request.getActualAvgWeightG());
        load.setWeightKg(request.getWeightKg());
        load.setGradingRuleId(active.getId());
        load.setLoadedBy(request.getLoadedBy());
        load.setRequestId(request.getRequestId());
        load.setRemark(request.getRemark());
        loadMapper.insert(load);

        FishingBin binBefore = copyOf(bin);
        bin.setLoadedKg(bin.getLoadedKg().add(request.getWeightKg()));
        // 库位状态与待检标识同步：装货即置 LOADING/FULL 并标记待检，一次更新不留中间态
        bin.setStatus(bin.getLoadedKg().compareTo(bin.getCapacityKg()) >= 0
                ? FishingBin.STATUS_FULL : FishingBin.STATUS_LOADING);
        bin.setPendingInspection(true);
        bin.setInspectedBy(null);
        bin.setInspectedTime(null);
        binMapper.updateById(bin);

        FishingReservation resvBefore = copyOf(reservation);
        reservation.setActualQtyKg(reservation.getActualQtyKg().add(request.getWeightKg()));
        reservationMapper.updateById(reservation);

        evidenceService.record(FishingChangeEvidence.ENTITY_LOAD, load.getId(),
                FishingChangeEvidence.ACTION_LOAD_RECORD, request.getRequestId(), request.getLoadedBy(),
                null, load, "现场分仓：实际均重" + request.getActualAvgWeightG() + "g判为规格"
                        + graded.getSpecCode() + "（规则" + active.getRuleNo() + "），"
                        + request.getWeightKg() + "kg入" + bin.getBinNo());
        evidenceService.record(FishingChangeEvidence.ENTITY_BIN, bin.getId(),
                FishingChangeEvidence.ACTION_BIN_LOAD, request.getRequestId(), request.getLoadedBy(),
                binBefore, bin, "库位" + bin.getBinNo() + "装入" + request.getWeightKg()
                        + "kg，状态与待检标识同步置位");
        evidenceService.record(FishingChangeEvidence.ENTITY_RESERVATION, reservation.getId(),
                FishingChangeEvidence.ACTION_LOAD_RECORD, request.getRequestId(), request.getLoadedBy(),
                resvBefore, reservation, "预约" + reservation.getResvNo() + "累计实际量"
                        + reservation.getActualQtyKg() + "kg");
        return load;
    }

    /**
     * 完成预约：核对实际量与计划量，短装（SHORT）/超装（OVER）自动生成差异单；
     * 数量一致不生成。全部预约完成后订单完成并冻结当时分级规则快照。
     * 重复完成幂等——每预约至多一条差异单。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingReservation complete(Long id, ReservationCompleteRequest request) {
        FishingReservation reservation = getOrThrow(id);
        if (FishingReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
            return reservation;
        }
        if (!FishingReservation.STATUS_LOCKED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("仅已锁定预约可完成，当前状态：" + reservation.getStatus());
        }
        List<FishingBin> occupied = binsOfReservation(reservation.getId());
        for (FishingBin bin : occupied) {
            if (Boolean.TRUE.equals(bin.getPendingInspection())) {
                throw new IllegalArgumentException("库位" + bin.getBinNo()
                        + "尚有货物待检验，须先完成检验再完成预约（库位状态与待检标识须一致）");
            }
        }
        FishingReservation before = copyOf(reservation);
        reservation.setStatus(FishingReservation.STATUS_COMPLETED);
        reservation.setCompletedTime(LocalDateTime.now());
        reservationMapper.updateById(reservation);

        BigDecimal diff = reservation.getActualQtyKg().subtract(reservation.getPlannedQtyKg());
        if (diff.signum() != 0) {
            FishingVariance variance = new FishingVariance();
            variance.setVarianceNo("FV-" + reservation.getResvNo());
            variance.setReservationId(reservation.getId());
            variance.setOrderId(reservation.getOrderId());
            variance.setVarianceType(diff.signum() < 0
                    ? FishingVariance.TYPE_SHORT : FishingVariance.TYPE_OVER);
            variance.setPlannedQtyKg(reservation.getPlannedQtyKg());
            variance.setActualQtyKg(reservation.getActualQtyKg());
            variance.setDiffQtyKg(diff.abs());
            variance.setStatus(FishingVariance.STATUS_OPEN);
            variance.setCreatedBy(request.getOperator());
            variance.setRequestId(request.getRequestId());
            variance.setRemark(diff.signum() < 0 ? "短装" : "超装");
            varianceMapper.insert(variance);
            evidenceService.record(FishingChangeEvidence.ENTITY_VARIANCE, variance.getId(),
                    FishingChangeEvidence.ACTION_VARIANCE_CREATE, request.getRequestId(), request.getOperator(),
                    null, variance, "预约" + reservation.getResvNo() + (diff.signum() < 0 ? "短装" : "超装")
                            + variance.getDiffQtyKg() + "kg，生成差异单" + variance.getVarianceNo());
        }
        // 释放本预约占用的库位：检验已完成的货物随交付离场，库位腾空可被后续订单使用
        for (FishingBin bin : occupied) {
            FishingBin binBefore = copyOf(bin);
            bin.setOccupiedOrderId(null);
            bin.setOccupiedResvId(null);
            bin.setLoadedKg(BigDecimal.ZERO);
            bin.setStatus(FishingBin.STATUS_EMPTY);
            binMapper.updateById(bin);
            evidenceService.record(FishingChangeEvidence.ENTITY_BIN, bin.getId(),
                    FishingChangeEvidence.ACTION_BIN_RELEASE, request.getRequestId(), request.getOperator(),
                    binBefore, bin, "预约完成交付，库位" + bin.getBinNo() + "腾空释放");
        }
        evidenceService.record(FishingChangeEvidence.ENTITY_RESERVATION, reservation.getId(),
                FishingChangeEvidence.ACTION_RESV_COMPLETE, request.getRequestId(), request.getOperator(),
                before, reservation, "预约" + reservation.getResvNo() + "完成：计划"
                        + reservation.getPlannedQtyKg() + "kg，实际" + reservation.getActualQtyKg() + "kg");
        orderService.completeIfAllDone(reservation.getOrderId(), request.getOperator(), request.getRequestId());
        return reservation;
    }

    /**
     * 临时换车：必须登记原因；换车记录与客户通知同一事务生成。
     * requestId 幂等——重试返回首次换车记录，不重复通知。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingVehicleChange changeVehicle(Long id, VehicleChangeRequest request) {
        FishingVehicleChange duplicated = vehicleChangeMapper.selectOne(
                new QueryWrapper<FishingVehicleChange>()
                        .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FishingReservation reservation = getOrThrow(id);
        if (FishingReservation.STATUS_COMPLETED.equals(reservation.getStatus())) {
            throw new IllegalArgumentException("已完成预约不可换车");
        }
        FishingOrder order = orderService.getOrThrow(reservation.getOrderId());
        FishingReservation before = copyOf(reservation);

        FishingVehicleChange change = new FishingVehicleChange();
        change.setReservationId(reservation.getId());
        change.setOrderId(reservation.getOrderId());
        change.setOldVehicleNo(reservation.getVehicleNo());
        change.setNewVehicleNo(request.getNewVehicleNo());
        change.setReason(request.getReason());
        change.setChangedBy(request.getChangedBy());
        change.setRequestId(request.getRequestId());
        vehicleChangeMapper.insert(change);

        reservation.setVehicleNo(request.getNewVehicleNo());
        reservationMapper.updateById(reservation);

        FishingNotification notification = new FishingNotification();
        notification.setNoticeNo("FN-" + change.getId());
        notification.setOrderId(order.getId());
        notification.setReservationId(reservation.getId());
        notification.setCustomerName(order.getCustomerName());
        notification.setChangeId(change.getId());
        notification.setMessage("订单" + order.getOrderNo() + "预约" + reservation.getResvNo()
                + "运输车辆由" + (change.getOldVehicleNo() == null ? "（未登记）" : change.getOldVehicleNo())
                + "临时变更为" + change.getNewVehicleNo() + "，原因：" + change.getReason() + "，请知悉。");
        notification.setStatus(FishingNotification.STATUS_SENT);
        notification.setRequestId(request.getRequestId());
        notificationMapper.insert(notification);

        evidenceService.record(FishingChangeEvidence.ENTITY_VEHICLE_CHANGE, change.getId(),
                FishingChangeEvidence.ACTION_VEHICLE_CHANGE, request.getRequestId(), request.getChangedBy(),
                before, reservation, "预约" + reservation.getResvNo() + "临时换车："
                        + change.getOldVehicleNo() + "→" + change.getNewVehicleNo() + "，原因：" + change.getReason());
        evidenceService.record(FishingChangeEvidence.ENTITY_NOTIFICATION, notification.getId(),
                FishingChangeEvidence.ACTION_NOTIFY_SEND, request.getRequestId(), request.getChangedBy(),
                null, notification, "已向客户" + order.getCustomerName() + "发送换车通知" + notification.getNoticeNo());
        return change;
    }

    public FishingReservation getOrThrow(Long id) {
        FishingReservation reservation = reservationMapper.selectById(id);
        if (reservation == null) {
            throw new IllegalArgumentException("捕捞预约不存在，id=" + id);
        }
        return reservation;
    }

    public List<FishingLoad> loadsOf(Long reservationId) {
        return loadMapper.selectList(new QueryWrapper<FishingLoad>()
                .eq("reservation_id", reservationId).orderByAsc("id"));
    }

    public FishingVariance varianceOf(Long reservationId) {
        return varianceMapper.selectOne(new QueryWrapper<FishingVariance>()
                .eq("reservation_id", reservationId).last("limit 1"));
    }

    public List<FishingVehicleChange> vehicleChangesOf(Long reservationId) {
        return vehicleChangeMapper.selectList(new QueryWrapper<FishingVehicleChange>()
                .eq("reservation_id", reservationId).orderByAsc("id"));
    }

    /**
     * 锁定选仓：指定库位须规格匹配且未被其他订单占用；缺省按规格挑最早登记的空闲库位。
     */
    private FishingBin pickBin(FishingReservation reservation, Long binId) {
        if (binId != null) {
            FishingBin bin = binMapper.selectById(binId);
            if (bin == null) {
                throw new IllegalArgumentException("库位不存在，id=" + binId);
            }
            requireBinUsable(bin, reservation);
            return bin;
        }
        List<FishingBin> candidates = binMapper.selectList(new QueryWrapper<FishingBin>()
                .eq("spec_code", reservation.getSpecCode()).orderByAsc("id"));
        for (FishingBin bin : candidates) {
            if (bin.getOccupiedOrderId() == null
                    || bin.getOccupiedOrderId().equals(reservation.getOrderId())) {
                return bin;
            }
        }
        throw new IllegalArgumentException("规格" + reservation.getSpecCode()
                + "没有可用库位（现有库位均被其他订单占用）");
    }

    private void requireBinUsable(FishingBin bin, FishingReservation reservation) {
        if (!bin.getSpecCode().equals(reservation.getSpecCode())) {
            throw new IllegalArgumentException("库位" + bin.getBinNo() + "对应规格" + bin.getSpecCode()
                    + "，与预约规格" + reservation.getSpecCode() + "不一致");
        }
        if (bin.getOccupiedOrderId() != null
                && !bin.getOccupiedOrderId().equals(reservation.getOrderId())) {
            throw new IllegalArgumentException("库位" + bin.getBinNo() + "已被订单id="
                    + bin.getOccupiedOrderId() + "的预约锁定占用，不能被其他订单占用");
        }
    }

    /**
     * 分仓路由：实际规格与锁定库位一致时入锁定库位；否则找实际规格对应库位——
     * 优先本订单已占用的，其次空闲库位（自动为本订单占用）；均被其他订单占用则拒绝。
     */
    private FishingBin routeBin(FishingReservation reservation, String actualSpec, BigDecimal weightKg) {
        FishingBin lockedBin = reservation.getBinId() == null ? null : binMapper.selectById(reservation.getBinId());
        FishingBin target;
        if (lockedBin != null && lockedBin.getSpecCode().equals(actualSpec)) {
            target = lockedBin;
        } else {
            List<FishingBin> candidates = binMapper.selectList(new QueryWrapper<FishingBin>()
                    .eq("spec_code", actualSpec).orderByAsc("id"));
            FishingBin own = null;
            FishingBin free = null;
            for (FishingBin bin : candidates) {
                if (bin.getOccupiedOrderId() != null
                        && bin.getOccupiedOrderId().equals(reservation.getOrderId())) {
                    own = bin;
                    break;
                }
                if (bin.getOccupiedOrderId() == null && free == null) {
                    free = bin;
                }
            }
            target = own != null ? own : free;
            if (target == null) {
                throw new IllegalArgumentException("实际规格" + actualSpec
                        + "的库位均被其他订单占用，无法分仓（预约锁定库位规格为"
                        + (lockedBin == null ? "无" : lockedBin.getSpecCode()) + "）");
            }
            if (target.getOccupiedOrderId() == null) {
                target.setOccupiedOrderId(reservation.getOrderId());
                target.setOccupiedResvId(reservation.getId());
            }
        }
        if (target.getLoadedKg().add(weightKg).compareTo(target.getCapacityKg()) > 0) {
            throw new IllegalArgumentException("库位" + target.getBinNo() + "容量不足：容量"
                    + target.getCapacityKg() + "kg，已装" + target.getLoadedKg()
                    + "kg，本次" + weightKg + "kg");
        }
        return target;
    }

    private List<FishingBin> binsOfReservation(Long reservationId) {
        return binMapper.selectList(new QueryWrapper<FishingBin>()
                .eq("occupied_resv_id", reservationId).orderByAsc("id"));
    }

    private String nextLoadNo(Long reservationId) {
        Long count = loadMapper.selectCount(new QueryWrapper<FishingLoad>()
                .eq("reservation_id", reservationId));
        return "FL-" + reservationId + "-" + (count + 1);
    }

    private FishingReservation copyOf(FishingReservation reservation) {
        FishingReservation copy = new FishingReservation();
        org.springframework.beans.BeanUtils.copyProperties(reservation, copy);
        return copy;
    }

    private FishingBin copyOf(FishingBin bin) {
        FishingBin copy = new FishingBin();
        org.springframework.beans.BeanUtils.copyProperties(bin, copy);
        return copy;
    }
}
