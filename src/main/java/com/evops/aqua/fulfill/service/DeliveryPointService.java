package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fulfill.dto.PointRegisterRequest;
import com.evops.aqua.fulfill.entity.DeliveryPoint;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.mapper.DeliveryPointMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货点主数据：登记后供履约子单指定与改点审批使用；requestId 幂等。
 */
@Service
public class DeliveryPointService {

    private final DeliveryPointMapper pointMapper;
    private final FulfillEvidenceService evidenceService;

    public DeliveryPointService(DeliveryPointMapper pointMapper, FulfillEvidenceService evidenceService) {
        this.pointMapper = pointMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public DeliveryPoint register(PointRegisterRequest request) {
        DeliveryPoint duplicated = pointMapper.selectOne(new QueryWrapper<DeliveryPoint>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        DeliveryPoint sameNo = pointMapper.selectOne(new QueryWrapper<DeliveryPoint>()
                .eq("point_no", request.getPointNo()).last("limit 1"));
        if (sameNo != null) {
            throw new IllegalArgumentException("收货点编号已存在：" + request.getPointNo());
        }
        DeliveryPoint point = new DeliveryPoint();
        point.setPointNo(request.getPointNo());
        point.setPointName(request.getPointName());
        point.setAddress(request.getAddress());
        point.setContactPerson(request.getContactPerson());
        point.setContactPhone(request.getContactPhone());
        point.setStatus(DeliveryPoint.STATUS_ACTIVE);
        point.setOperator(request.getOperator());
        point.setRequestId(request.getRequestId());
        point.setRemark(request.getRemark());
        pointMapper.insert(point);
        evidenceService.record(FulfillChangeEvidence.ENTITY_DELIVERY_POINT, point.getId(),
                FulfillChangeEvidence.ACTION_POINT_REGISTER, request.getRequestId(), request.getOperator(),
                null, point, "登记收货点" + point.getPointNo() + "（" + point.getPointName() + "）");
        return point;
    }

    public DeliveryPoint activePointOrThrow(Long pointId) {
        DeliveryPoint point = pointMapper.selectById(pointId);
        if (point == null) {
            throw new IllegalArgumentException("收货点不存在，id=" + pointId);
        }
        if (!DeliveryPoint.STATUS_ACTIVE.equals(point.getStatus())) {
            throw new IllegalArgumentException("收货点" + point.getPointNo() + "已停用，不能用于履约拆单或改点");
        }
        return point;
    }

    public List<DeliveryPoint> list(String status) {
        QueryWrapper<DeliveryPoint> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return pointMapper.selectList(wrapper);
    }
}
