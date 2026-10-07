package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.NotificationAckRequest;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.entity.FishingNotification;
import com.evops.aqua.fishing.mapper.FishingNotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 客户通知：临时换车触发；客户签收留回执（签收人/时间/回执备注），
 * 重复签收幂等返回首次回执，不覆盖。
 */
@Service
public class FishingNotificationService {

    private final FishingNotificationMapper notificationMapper;
    private final FishingEvidenceService evidenceService;

    public FishingNotificationService(FishingNotificationMapper notificationMapper,
                                      FishingEvidenceService evidenceService) {
        this.notificationMapper = notificationMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 客户签收：留回执；已签收通知重复签收不覆盖首次回执。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingNotification ack(Long id, NotificationAckRequest request) {
        FishingNotification notification = getOrThrow(id);
        if (FishingNotification.STATUS_ACKED.equals(notification.getStatus())) {
            return notification;
        }
        FishingNotification before = copyOf(notification);
        notification.setStatus(FishingNotification.STATUS_ACKED);
        notification.setAckBy(request.getAckBy());
        notification.setAckTime(LocalDateTime.now());
        notification.setAckRemark(request.getAckRemark());
        notificationMapper.updateById(notification);
        evidenceService.record(FishingChangeEvidence.ENTITY_NOTIFICATION, notification.getId(),
                FishingChangeEvidence.ACTION_NOTIFY_ACK, request.getRequestId(), request.getAckBy(),
                before, notification, "客户签收通知" + notification.getNoticeNo() + "，留回执");
        return notification;
    }

    public FishingNotification getOrThrow(Long id) {
        FishingNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new IllegalArgumentException("客户通知不存在，id=" + id);
        }
        return notification;
    }

    public List<FishingNotification> list(Long orderId, String status) {
        QueryWrapper<FishingNotification> wrapper = new QueryWrapper<>();
        wrapper.eq(orderId != null, "order_id", orderId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return notificationMapper.selectList(wrapper);
    }

    private FishingNotification copyOf(FishingNotification notification) {
        FishingNotification copy = new FishingNotification();
        org.springframework.beans.BeanUtils.copyProperties(notification, copy);
        return copy;
    }
}
