package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fulfill.dto.DeliverySplitRequest;
import com.evops.aqua.fulfill.dto.PointChangeReviewRequest;
import com.evops.aqua.fulfill.dto.PointChangeSubmitRequest;
import com.evops.aqua.fulfill.entity.DeliveryPoint;
import com.evops.aqua.fulfill.entity.DeliveryPointChange;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.entity.FulfillDelivery;
import com.evops.aqua.fulfill.entity.FulfillHandover;
import com.evops.aqua.fulfill.entity.FulfillOrder;
import com.evops.aqua.fulfill.mapper.DeliveryPointChangeMapper;
import com.evops.aqua.fulfill.mapper.FulfillDeliveryMapper;
import com.evops.aqua.fulfill.mapper.FulfillHandoverMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 履约子单的再拆与替换收货点审批：
 * - 再拆只按数量切分（同收货点同规格、单价不变），金额按数量比例分摊、尾差归原行——订单总额口径不变；
 * - 改点必须 PENDING 审批，审批人+意见缺一不可，通过只改收货点，数量与金额一律不动。
 */
@Service
public class FulfillDeliveryService {

    private final FulfillDeliveryMapper deliveryMapper;
    private final DeliveryPointChangeMapper changeMapper;
    private final FulfillHandoverMapper handoverMapper;
    private final FulfillOrderService orderService;
    private final DeliveryPointService pointService;
    private final FulfillEvidenceService evidenceService;

    public FulfillDeliveryService(FulfillDeliveryMapper deliveryMapper,
                                  DeliveryPointChangeMapper changeMapper,
                                  FulfillHandoverMapper handoverMapper,
                                  FulfillOrderService orderService,
                                  DeliveryPointService pointService,
                                  FulfillEvidenceService evidenceService) {
        this.deliveryMapper = deliveryMapper;
        this.changeMapper = changeMapper;
        this.handoverMapper = handoverMapper;
        this.orderService = orderService;
        this.pointService = pointService;
        this.evidenceService = evidenceService;
    }

    /**
     * 子单再拆：原行保留尾差数量与尾差金额，子行按数量比例分摊金额。
     * requestId 幂等：重复提交返回原行，不产生第二批子行。
     */
    @Transactional(rollbackFor = Exception.class)
    public FulfillDelivery split(Long deliveryId, DeliverySplitRequest request) {
        FulfillDelivery parent = getOrThrow(deliveryId);
        FulfillOrder order = orderService.requireOpen(orderService.getOrThrow(parent.getOrderId()));

        // 派生子行请求号：父拆分请求号#c1，用于整笔动作查重（与 fishing 预约 #行序 同构）
        FulfillDelivery duplicated = deliveryMapper.selectOne(new QueryWrapper<FulfillDelivery>()
                .eq("request_id", request.getRequestId() + "#c1").last("limit 1"));
        if (duplicated != null) {
            return parent;
        }
        if (!FulfillDelivery.STATUS_PLANNED.equals(parent.getStatus())) {
            throw new IllegalArgumentException("子单" + parent.getDeliveryNo()
                    + "已开始履约（" + parent.getStatus() + "），不能再拆");
        }
        if (parent.getIntakeNetKg().signum() > 0) {
            throw new IllegalArgumentException("子单" + parent.getDeliveryNo() + "已有入库净重，不能再拆");
        }
        if (hasHandover(deliveryId)) {
            throw new IllegalArgumentException("子单" + parent.getDeliveryNo() + "已交接，不能再拆");
        }

        BigDecimal childQtySum = BigDecimal.ZERO;
        int idx = 0;
        for (DeliverySplitRequest.SplitChild child : request.getChildren()) {
            idx++;
            if (child.getPlannedNetKg() == null || child.getPlannedNetKg().signum() <= 0) {
                throw new IllegalArgumentException("第" + idx + "个子行数量必须大于0");
            }
            childQtySum = childQtySum.add(child.getPlannedNetKg());
        }
        if (childQtySum.compareTo(parent.getPlannedNetKg()) >= 0) {
            throw new IllegalArgumentException("子行数量合计" + childQtySum
                    + "kg 必须小于原行数量" + parent.getPlannedNetKg() + "kg（原行须保留正尾差）");
        }

        FulfillDelivery before = copyOf(parent);
        BigDecimal childAmountSum = BigDecimal.ZERO;
        int seq = 0;
        for (DeliverySplitRequest.SplitChild child : request.getChildren()) {
            seq++;
            BigDecimal childAmount = child.getPlannedNetKg()
                    .multiply(parent.getLineAmount())
                    .divide(parent.getPlannedNetKg(), 2, RoundingMode.HALF_UP);
            childAmountSum = childAmountSum.add(childAmount);

            FulfillDelivery childDelivery = new FulfillDelivery();
            childDelivery.setDeliveryNo(parent.getDeliveryNo() + "-C" + seq);
            childDelivery.setOrderId(parent.getOrderId());
            childDelivery.setParentId(parent.getId());
            childDelivery.setSourceReservationId(parent.getSourceReservationId());
            childDelivery.setSpecCode(parent.getSpecCode());
            childDelivery.setDeliveryPointId(parent.getDeliveryPointId());
            childDelivery.setPlannedNetKg(child.getPlannedNetKg());
            childDelivery.setUnitPrice(parent.getUnitPrice());
            childDelivery.setLineAmount(childAmount);
            childDelivery.setIntakeNetKg(BigDecimal.ZERO);
            childDelivery.setStatus(FulfillDelivery.STATUS_PLANNED);
            childDelivery.setRequestId(request.getRequestId() + "#c" + seq);
            deliveryMapper.insert(childDelivery);
            evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_DELIVERY, childDelivery.getId(),
                    FulfillChangeEvidence.ACTION_DELIVERY_SPLIT, childDelivery.getRequestId(),
                    request.getOperator(), null, childDelivery,
                    "子单" + parent.getDeliveryNo() + "再拆出" + childDelivery.getDeliveryNo()
                            + "：数量" + childDelivery.getPlannedNetKg() + "kg，分摊金额"
                            + childAmount + "元，单价不变");
        }

        // 尾差（数量与金额）全部归原行，保证 Σ行金额 ≡ 订单总额
        BigDecimal parentNewQty = parent.getPlannedNetKg().subtract(childQtySum);
        BigDecimal parentNewAmount = parent.getLineAmount().subtract(childAmountSum);
        parent.setPlannedNetKg(parentNewQty);
        parent.setLineAmount(parentNewAmount);
        deliveryMapper.updateById(parent);
        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_DELIVERY, parent.getId(),
                FulfillChangeEvidence.ACTION_DELIVERY_SPLIT, request.getRequestId(), request.getOperator(),
                before, parent, "子单" + parent.getDeliveryNo() + "再拆后保留尾差：数量"
                        + parentNewQty + "kg，金额" + parentNewAmount + "元；订单金额口径不变");
        return parent;
    }

    /**
     * 提交替换收货点申请：停留 PENDING；同一子单同时只允许一条在途申请。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPointChange submitPointChange(Long deliveryId, PointChangeSubmitRequest request) {
        DeliveryPointChange duplicated = changeMapper.selectOne(new QueryWrapper<DeliveryPointChange>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FulfillDelivery delivery = getOrThrow(deliveryId);
        orderService.requireOpen(orderService.getOrThrow(delivery.getOrderId()));
        if (hasHandover(deliveryId)) {
            throw new IllegalArgumentException("子单" + delivery.getDeliveryNo() + "已交接，不能替换收货点");
        }
        DeliveryPoint toPoint = pointService.activePointOrThrow(request.getToPointId());
        if (toPoint.getId().equals(delivery.getDeliveryPointId())) {
            throw new IllegalArgumentException("目标收货点" + toPoint.getPointNo() + "与当前收货点相同，无需替换");
        }
        Long pendingCount = changeMapper.selectCount(new QueryWrapper<DeliveryPointChange>()
                .eq("delivery_id", deliveryId).eq("status", DeliveryPointChange.STATUS_PENDING));
        if (pendingCount > 0) {
            throw new IllegalArgumentException("子单" + delivery.getDeliveryNo() + "已有待审批的收货点替换申请");
        }

        DeliveryPointChange change = new DeliveryPointChange();
        change.setChangeNo(nextChangeNo());
        change.setDeliveryId(deliveryId);
        change.setFromPointId(delivery.getDeliveryPointId());
        change.setToPointId(request.getToPointId());
        change.setReason(request.getReason());
        change.setStatus(DeliveryPointChange.STATUS_PENDING);
        change.setSubmittedBy(request.getSubmittedBy());
        change.setRequestId(request.getRequestId());
        changeMapper.insert(change);
        evidenceService.record(FulfillChangeEvidence.ENTITY_POINT_CHANGE, change.getId(),
                FulfillChangeEvidence.ACTION_POINT_CHANGE_SUBMIT, request.getRequestId(),
                request.getSubmittedBy(), null, change, "子单" + delivery.getDeliveryNo()
                        + "申请替换收货点：" + change.getFromPointId() + "→" + change.getToPointId()
                        + "，原因：" + request.getReason());
        return change;
    }

    /**
     * 审批通过：只改子单收货点，数量/单价/金额一律不变。重复审批幂等返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPointChange approve(Long changeId, PointChangeReviewRequest request) {
        DeliveryPointChange change = getChangeOrThrow(changeId);
        if (!DeliveryPointChange.STATUS_PENDING.equals(change.getStatus())) {
            return change;
        }
        FulfillDelivery delivery = getOrThrow(change.getDeliveryId());
        FulfillDelivery beforeDelivery = copyOf(delivery);

        change.setStatus(DeliveryPointChange.STATUS_APPROVED);
        change.setApprovedBy(request.getApprovedBy());
        change.setApproveRemark(request.getApproveRemark());
        change.setApprovedTime(LocalDateTime.now());
        changeMapper.updateById(change);

        delivery.setDeliveryPointId(change.getToPointId());
        deliveryMapper.updateById(delivery);

        evidenceService.record(FulfillChangeEvidence.ENTITY_POINT_CHANGE, change.getId(),
                FulfillChangeEvidence.ACTION_POINT_CHANGE_APPROVE, request.getRequestId(),
                request.getApprovedBy(), null, change,
                "替换收货点审批通过：" + change.getFromPointId() + "→" + change.getToPointId()
                        + "，意见：" + request.getApproveRemark());
        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_DELIVERY, delivery.getId(),
                FulfillChangeEvidence.ACTION_DELIVERY_POINT_REPLACE, request.getRequestId(),
                request.getApprovedBy(), beforeDelivery, delivery,
                "子单" + delivery.getDeliveryNo() + "收货点变更，数量" + delivery.getPlannedNetKg()
                        + "kg、单价" + delivery.getUnitPrice() + "、金额" + delivery.getLineAmount()
                        + "元均不变");
        return change;
    }

    /**
     * 审批驳回：子单收货点不变。重复审批幂等返回。
     */
    @Transactional(rollbackFor = Exception.class)
    public DeliveryPointChange reject(Long changeId, PointChangeReviewRequest request) {
        DeliveryPointChange change = getChangeOrThrow(changeId);
        if (!DeliveryPointChange.STATUS_PENDING.equals(change.getStatus())) {
            return change;
        }
        change.setStatus(DeliveryPointChange.STATUS_REJECTED);
        change.setApprovedBy(request.getApprovedBy());
        change.setApproveRemark(request.getApproveRemark());
        change.setApprovedTime(LocalDateTime.now());
        changeMapper.updateById(change);
        evidenceService.record(FulfillChangeEvidence.ENTITY_POINT_CHANGE, change.getId(),
                FulfillChangeEvidence.ACTION_POINT_CHANGE_REJECT, request.getRequestId(),
                request.getApprovedBy(), null, change,
                "替换收货点审批驳回，意见：" + request.getApproveRemark());
        return change;
    }

    public boolean hasHandover(Long deliveryId) {
        return handoverMapper.selectCount(new QueryWrapper<FulfillHandover>()
                .eq("delivery_id", deliveryId)) > 0;
    }

    public FulfillDelivery getOrThrow(Long id) {
        FulfillDelivery delivery = deliveryMapper.selectById(id);
        if (delivery == null) {
            throw new IllegalArgumentException("履约子单不存在，id=" + id);
        }
        return delivery;
    }

    public DeliveryPointChange getChangeOrThrow(Long id) {
        DeliveryPointChange change = changeMapper.selectById(id);
        if (change == null) {
            throw new IllegalArgumentException("收货点替换申请不存在，id=" + id);
        }
        return change;
    }

    public List<DeliveryPointChange> changesOf(Long deliveryId, String status) {
        QueryWrapper<DeliveryPointChange> wrapper = new QueryWrapper<>();
        wrapper.eq(deliveryId != null, "delivery_id", deliveryId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return changeMapper.selectList(wrapper);
    }

    private String nextChangeNo() {
        Long count = changeMapper.selectCount(new QueryWrapper<>());
        return "FPC-" + (count + 1);
    }

    private FulfillDelivery copyOf(FulfillDelivery delivery) {
        FulfillDelivery copy = new FulfillDelivery();
        org.springframework.beans.BeanUtils.copyProperties(delivery, copy);
        return copy;
    }
}
