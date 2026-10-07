package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.entity.FulfillOrder;
import com.evops.aqua.fulfill.entity.FulfillOutbox;
import com.evops.aqua.fulfill.external.ExternalUnavailableException;
import com.evops.aqua.fulfill.external.MarineManageGateway;
import com.evops.aqua.fulfill.mapper.FulfillOrderMapper;
import com.evops.aqua.fulfill.mapper.FulfillOutboxMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 海水养殖管理同步发件箱：
 * - 建单事务只写 PENDING 记录，不在业务事务内发起外部调用；
 * - 发送在事务外执行；成功/失败各用独立事务条件更新（只有 PENDING 行可变更），
 *   并发双跑也只有一方推进，对端按确定性 eventId 幂等——恢复后绝不重复通知；
 * - 失败只累加 attempts/last_error 并独立事务留 SYNC_FAIL 证据，随后向上抛出，由调用方明确报失败。
 */
@Service
public class OutboxService {

    private final FulfillOutboxMapper outboxMapper;
    private final FulfillOrderMapper orderMapper;
    private final MarineManageGateway gateway;
    private final FulfillEvidenceService evidenceService;
    private final ObjectMapper objectMapper;

    public OutboxService(FulfillOutboxMapper outboxMapper,
                         FulfillOrderMapper orderMapper,
                         MarineManageGateway gateway,
                         FulfillEvidenceService evidenceService,
                         ObjectMapper objectMapper) {
        this.outboxMapper = outboxMapper;
        this.orderMapper = orderMapper;
        this.gateway = gateway;
        this.evidenceService = evidenceService;
        this.objectMapper = objectMapper;
    }

    /**
     * 建单成功（业务事务已提交）后调用：尝试把该订单的拆单事件同步到海水养殖管理。
     * 本方法不加事务——外部调用在事务外执行；SENT 记录直接跳过。
     * 外部无响应时抛 ExternalUnavailableException，订单保持 PENDING_SYNC，等待 scan 恢复。
     */
    public void dispatchForOrder(Long orderId) {
        FulfillOutbox outbox = outboxMapper.selectOne(new QueryWrapper<FulfillOutbox>()
                .eq("biz_type", FulfillOutbox.BIZ_SPLIT).eq("biz_id", orderId).last("limit 1"));
        if (outbox == null || FulfillOutbox.STATUS_SENT.equals(outbox.getStatus())) {
            return;
        }
        dispatchOne(outbox);
    }

    /**
     * 扫描全部 PENDING 事件逐条重发：单条失败不影响其他事件（恢复后只发未确认的，不重复通知）。
     */
    public Map<String, Object> scan() {
        List<FulfillOutbox> pending = outboxMapper.selectList(new QueryWrapper<FulfillOutbox>()
                .eq("status", FulfillOutbox.STATUS_PENDING).orderByAsc("id"));
        int sent = 0;
        int failed = 0;
        List<Long> sentIds = new ArrayList<>();
        List<Map<String, Object>> failedItems = new ArrayList<>();
        for (FulfillOutbox outbox : pending) {
            try {
                dispatchOne(outbox);
                sent++;
                sentIds.add(outbox.getId());
            } catch (ExternalUnavailableException ex) {
                failed++;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("outboxId", outbox.getId());
                item.put("eventId", outbox.getEventId());
                item.put("error", ex.getMessage());
                failedItems.add(item);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scannedCount", pending.size());
        result.put("sentCount", sent);
        result.put("failedCount", failed);
        result.put("sentIds", sentIds);
        result.put("failedItems", failedItems);
        return result;
    }

    /** Quartz 入口（只调度不关心返回值）。 */
    public void scanNow() {
        scan();
    }

    private void dispatchOne(FulfillOutbox outbox) {
        Map<String, Object> payload = parsePayload(outbox.getPayload());
        try {
            gateway.registerSplit(outbox.getEventId(), payload);
        } catch (ExternalUnavailableException ex) {
            markFailureTx(outbox.getId(), outbox.getRequestId(), ex.getMessage());
            throw ex;
        }
        markSentTx(outbox.getId());
    }

    /**
     * 成功确认（独立事务 + 条件更新）：仅 PENDING→SENT，防止并发重复推进。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markSentTx(Long outboxId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<FulfillOutbox> uw = Wrappers.lambdaUpdate(FulfillOutbox.class)
                .eq(FulfillOutbox::getId, outboxId)
                .eq(FulfillOutbox::getStatus, FulfillOutbox.STATUS_PENDING)
                .set(FulfillOutbox::getStatus, FulfillOutbox.STATUS_SENT)
                .set(FulfillOutbox::getSentTime, now)
                .setSql("attempts = attempts + 1");
        int rows = outboxMapper.update(null, uw);
        if (rows == 0) {
            // 已被其他执行者确认：跳过订单推进与证据，不重复通知
            return;
        }
        FulfillOutbox outbox = outboxMapper.selectById(outboxId);
        LambdaUpdateWrapper<FulfillOrder> ow = Wrappers.lambdaUpdate(FulfillOrder.class)
                .eq(FulfillOrder::getId, outbox.getBizId())
                .eq(FulfillOrder::getStatus, FulfillOrder.STATUS_PENDING_SYNC)
                .set(FulfillOrder::getStatus, FulfillOrder.STATUS_OPEN)
                .set(FulfillOrder::getSyncedTime, now);
        int orderRows = orderMapper.update(null, ow);
        FulfillOrder order = orderMapper.selectById(outbox.getBizId());
        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_OUTBOX, outboxId,
                FulfillChangeEvidence.ACTION_SYNC_OK, outbox.getRequestId(),
                FulfillChangeEvidence.OPERATOR_GATEWAY, null, outbox,
                "拆单事件" + outbox.getEventId() + "已由海水养殖管理确认（" + gateway.mode()
                        + "），订单" + (order == null ? outbox.getBizId() : order.getOrderNo())
                        + (orderRows == 1 ? "转为OPEN" : "已是OPEN/完成态，本次仅确认发件箱"));
    }

    /**
     * 失败记账（独立事务 + 条件更新）：业务事务即使回滚，失败证据也必须留下。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void markFailureTx(Long outboxId, String requestId, String error) {
        String truncated = error == null ? "外部无响应" : error.substring(0, Math.min(error.length(), 900));
        LambdaUpdateWrapper<FulfillOutbox> uw = Wrappers.lambdaUpdate(FulfillOutbox.class)
                .eq(FulfillOutbox::getId, outboxId)
                .eq(FulfillOutbox::getStatus, FulfillOutbox.STATUS_PENDING)
                .set(FulfillOutbox::getLastError, truncated)
                .setSql("attempts = attempts + 1");
        int rows = outboxMapper.update(null, uw);
        if (rows == 0) {
            return;
        }
        FulfillOutbox outbox = outboxMapper.selectById(outboxId);
        evidenceService.record(FulfillChangeEvidence.ENTITY_FULFILL_OUTBOX, outboxId,
                FulfillChangeEvidence.ACTION_SYNC_FAIL, requestId,
                FulfillChangeEvidence.OPERATOR_GATEWAY, null, outbox,
                "拆单事件" + outbox.getEventId() + "同步海水养殖管理失败：" + truncated
                        + "（第" + outbox.getAttempts() + "次尝试，保持PENDING等待恢复重发）");
    }

    public List<FulfillOutbox> list(String status) {
        QueryWrapper<FulfillOutbox> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return outboxMapper.selectList(wrapper);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception ex) {
            throw new ExternalUnavailableException("拆单事件载荷解析失败：" + ex.getMessage());
        }
    }
}
