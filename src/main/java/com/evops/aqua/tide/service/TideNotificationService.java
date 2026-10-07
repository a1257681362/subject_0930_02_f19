package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.dto.AckRequest;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideNotification;
import com.evops.aqua.tide.mapper.TideNotificationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 窗口关闭通知：班组签收留回执（签收人/签收时间/回执备注）。
 * 重复签收幂等返回同一回执，不覆盖首次签收信息。
 */
@Service
public class TideNotificationService {

    private final TideNotificationMapper notificationMapper;
    private final TideEvidenceService evidenceService;

    public TideNotificationService(TideNotificationMapper notificationMapper,
                                   TideEvidenceService evidenceService) {
        this.notificationMapper = notificationMapper;
        this.evidenceService = evidenceService;
    }

    /** 班组签收：留回执（签收人+签收时间+回执备注）；重复签收幂等返回同一回执。 */
    @Transactional(rollbackFor = Exception.class)
    public TideNotification ack(Long notificationId, AckRequest request) {
        TideNotification notification = getOrThrow(notificationId);
        if (TideNotification.STATUS_ACKED.equals(notification.getStatus())) {
            return notification; // 幂等：重复签收返回同一回执，不覆盖首次签收
        }
        TideNotification before = copyOf(notification);
        notification.setStatus(TideNotification.STATUS_ACKED);
        notification.setAckBy(request.getOperator());
        notification.setAckTime(LocalDateTime.now());
        notification.setAckRemark(request.getAckRemark());
        notificationMapper.updateById(notification);

        evidenceService.record(TideChangeEvidence.ENTITY_NOTIFICATION, notification.getId(),
                TideChangeEvidence.ACTION_ACK_NOTIFICATION, null, request.getOperator(),
                before, notification,
                "班组" + notification.getCrewName() + "签收窗口关闭通知" + notification.getNoticeNo()
                        + "，回执[" + request.getAckRemark() + "]，签收人=" + request.getOperator());
        return notification;
    }

    public TideNotification getOrThrow(Long id) {
        TideNotification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new IllegalArgumentException("通知不存在，id=" + id);
        }
        return notification;
    }

    public List<TideNotification> list(String crewName, String status, Long windowId) {
        QueryWrapper<TideNotification> wrapper = new QueryWrapper<>();
        wrapper.eq(crewName != null && !crewName.trim().isEmpty(), "crew_name", crewName);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.eq(windowId != null, "window_id", windowId);
        wrapper.orderByAsc("id");
        return notificationMapper.selectList(wrapper);
    }

    private TideNotification copyOf(TideNotification source) {
        TideNotification copy = new TideNotification();
        copy.setId(source.getId());
        copy.setNoticeNo(source.getNoticeNo());
        copy.setWindowId(source.getWindowId());
        copy.setScheduleId(source.getScheduleId());
        copy.setCrewName(source.getCrewName());
        copy.setMessage(source.getMessage());
        copy.setStatus(source.getStatus());
        copy.setAckBy(source.getAckBy());
        copy.setAckTime(source.getAckTime());
        copy.setAckRemark(source.getAckRemark());
        copy.setRequestId(source.getRequestId());
        return copy;
    }
}
