package com.evops.aqua.release.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.release.dto.ReleaseShipRequest;
import com.evops.aqua.release.entity.ReleaseChangeEvidence;
import com.evops.aqua.release.entity.ReleaseOrder;
import com.evops.aqua.release.entity.ReleaseShipment;
import com.evops.aqua.release.mapper.ReleaseOrderMapper;
import com.evops.aqua.release.mapper.ReleaseShipmentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 批次进入发货：发货登记是批次进入发货的唯一出口。
 * 硬门——检验单必须已由授权签字人电子签名（SIGNED）才允许发货；
 * 未签名（草稿/已提交/退回）一律拒绝。一单一批发货一次，requestId 强制幂等。
 */
@Service
public class ReleaseShippingService {

    private final ReleaseShipmentMapper shipmentMapper;
    private final ReleaseOrderMapper orderMapper;
    private final ReleaseOrderService orderService;
    private final ReleaseEvidenceService evidenceService;

    public ReleaseShippingService(ReleaseShipmentMapper shipmentMapper,
                                  ReleaseOrderMapper orderMapper,
                                  ReleaseOrderService orderService,
                                  ReleaseEvidenceService evidenceService) {
        this.shipmentMapper = shipmentMapper;
        this.orderMapper = orderMapper;
        this.orderService = orderService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReleaseShipment ship(Long orderId, ReleaseShipRequest request) {
        // requestId 强制幂等：重复请求返回首次发货记录
        if (request.getRequestId() != null && !request.getRequestId().trim().isEmpty()) {
            List<ReleaseShipment> same = shipmentMapper.selectList(new QueryWrapper<ReleaseShipment>()
                    .eq("request_id", request.getRequestId()));
            if (!same.isEmpty()) {
                return same.get(0);
            }
        }

        ReleaseOrder before = orderService.getOrThrow(orderId);
        ReleaseOrder order = orderService.getOrThrow(orderId);

        // 已发过货：一单一批只发货一次
        ReleaseShipment existing = shipmentMapper.selectOne(new QueryWrapper<ReleaseShipment>()
                .eq("order_id", orderId));
        if (existing != null) {
            return existing;
        }

        // 硬门：只有授权电子签名后（SIGNED）才允许发货
        if (!ReleaseOrder.STATUS_SIGNED.equals(order.getStatus())
                && !ReleaseOrder.STATUS_SHIPPED.equals(order.getStatus())) {
            throw new IllegalArgumentException("检验单" + order.getOrderNo()
                    + "尚未完成授权电子签名（当前状态=" + order.getStatus()
                    + "），批次不能进入发货");
        }

        ReleaseShipment shipment = new ReleaseShipment();
        shipment.setShipmentNo("SHP-O" + order.getId());
        shipment.setOrderId(order.getId());
        shipment.setBatchId(order.getBatchId());
        shipment.setShipDate(request.getShipDate());
        shipment.setQuantity(request.getQuantity());
        shipment.setConsignee(request.getConsignee());
        shipment.setOperator(request.getOperator().trim());
        shipment.setRequestId(request.getRequestId());
        shipmentMapper.insert(shipment);

        order.setStatus(ReleaseOrder.STATUS_SHIPPED);
        order.setShipmentId(shipment.getId());
        orderMapper.updateById(order);

        evidenceService.record(ReleaseChangeEvidence.ENTITY_SHIPMENT, shipment.getId(),
                ReleaseChangeEvidence.ACTION_SHIP, request.getRequestId(),
                request.getOperator().trim(), before, order,
                "电子签名（" + order.getSignatureNo() + "，签字人" + order.getSignerName()
                        + "）后批次进入发货，发货单号" + shipment.getShipmentNo());
        return shipment;
    }

    public List<ReleaseShipment> listShipments(Long batchId, Long orderId) {
        QueryWrapper<ReleaseShipment> w = new QueryWrapper<>();
        w.eq(batchId != null, "batch_id", batchId);
        w.eq(orderId != null, "order_id", orderId);
        w.orderByAsc("id");
        return shipmentMapper.selectList(w);
    }
}
