package com.evops.aqua.sensor.service;

import com.evops.aqua.sensor.dto.SensorRiskWatchToggleRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorRiskWatch;
import com.evops.aqua.sensor.mapper.SensorRiskWatchMapper;
import com.evops.aqua.sensor.web.SensorAccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 离线归并高风险观察开关。
 * 观察项固定为「传感器补传造成日报重复」：置为观察后——
 * 1) 离线补传归并入口一律拒绝（整包不归并、读数不新增、日报不重算），拒绝事实独立事务留痕；
 * 2) 日报汇总重算同步限制（复核确认只留痕，日报待解除观察后再补算），已审核区间本就冻结；
 * 3) 查询、设备/版本档案维护、证据追加不受影响，已有凭证只追加不覆盖。
 * 无开关行视为未观察（与初始放开语义一致）。
 */
@Service
public class SensorRiskWatchService {

    private final SensorRiskWatchMapper watchMapper;
    private final SensorEvidenceService evidenceService;

    public SensorRiskWatchService(SensorRiskWatchMapper watchMapper,
                                  SensorEvidenceService evidenceService) {
        this.watchMapper = watchMapper;
        this.evidenceService = evidenceService;
    }

    public SensorRiskWatch getWatch() {
        SensorRiskWatch watch = watchMapper.selectById(SensorRiskWatch.SINGLETON_ID);
        if (watch == null) {
            watch = new SensorRiskWatch();
            watch.setId(SensorRiskWatch.SINGLETON_ID);
            watch.setWatchSubject(SensorRiskWatch.SUBJECT_RETRANSMIT_DUPLICATE);
            watch.setWatchEnabled(Boolean.FALSE);
            watch.setChangedReason("无开关行，按初始未观察处理");
        }
        return watch;
    }

    public boolean isRestricted() {
        return Boolean.TRUE.equals(getWatch().getWatchEnabled());
    }

    /** 置为高风险观察：离线归并与日报汇总同步限制。 */
    @Transactional(rollbackFor = Exception.class)
    public SensorRiskWatch watchOn(SensorRiskWatchToggleRequest request) {
        return toggle(true, request, SensorChangeEvidence.ACTION_WATCH_ON,
                "置为高风险观察：观察项[" + SensorRiskWatch.SUBJECT_RETRANSMIT_DUPLICATE
                        + "]，离线补传归并与日报汇总同步限制");
    }

    /** 解除高风险观察：恢复离线归并，未审核日报可再随补录/复核重算。 */
    @Transactional(rollbackFor = Exception.class)
    public SensorRiskWatch watchOff(SensorRiskWatchToggleRequest request) {
        return toggle(false, request, SensorChangeEvidence.ACTION_WATCH_OFF,
                "解除高风险观察：观察项[" + SensorRiskWatch.SUBJECT_RETRANSMIT_DUPLICATE
                        + "]，恢复离线归并与日报汇总");
    }

    /**
     * 离线归并入口闸门：观察期间拒绝整包归并，独立事务留痕后抛 403。
     * 与调用地址无关——任何路径进入归并逻辑都先过本闸门。
     */
    public void requireMergeAllowed(String deviceCode, String requestId) {
        if (!isRestricted()) {
            return;
        }
        String detail = "高风险观察[" + SensorRiskWatch.SUBJECT_RETRANSMIT_DUPLICATE
                + "]生效：离线补传归并已限制，拒绝设备[" + deviceCode + "]上传包归并（requestId="
                + requestId + "），读数不新增、日报汇总同步冻结，仅查询/审核/留痕可用";
        evidenceService.recordStandalone(SensorChangeEvidence.ENTITY_RISK_WATCH,
                SensorRiskWatch.SINGLETON_ID, SensorChangeEvidence.ACTION_DENIED,
                requestId, null, null, detail);
        throw new SensorAccessDeniedException(
                "离线归并高风险观察期间（传感器补传造成日报重复），暂停受理离线补传归并，日报汇总同步限制");
    }

    /**
     * 日报汇总闸门：观察期间未审核日报也不重算，独立事务留痕后抛 403。
     * REVIEWED 日报在调用方先行冻结返回，不触发本闸门。
     */
    public void requireAggregationAllowed(Long pondId, LocalDate reportDate, String metricType,
                                          String requestId) {
        if (!isRestricted()) {
            return;
        }
        String detail = "高风险观察[" + SensorRiskWatch.SUBJECT_RETRANSMIT_DUPLICATE
                + "]生效：日报汇总已限制，拒绝重算池塘[" + pondId + "]" + reportDate
                + "「" + metricType + "」日报（requestId=" + requestId + "），待解除观察后再补算";
        evidenceService.recordStandalone(SensorChangeEvidence.ENTITY_RISK_WATCH,
                SensorRiskWatch.SINGLETON_ID, SensorChangeEvidence.ACTION_DENIED,
                requestId, null, null, detail);
        throw new SensorAccessDeniedException(
                "离线归并高风险观察期间（传感器补传造成日报重复），日报汇总同步限制，暂不重算");
    }

    private SensorRiskWatch toggle(boolean enabled, SensorRiskWatchToggleRequest request,
                                   String action, String detailPrefix) {
        SensorRiskWatch before = copyOf(getWatch());
        SensorRiskWatch watch = getWatch();
        boolean existed = watchMapper.selectById(SensorRiskWatch.SINGLETON_ID) != null;
        watch.setWatchSubject(SensorRiskWatch.SUBJECT_RETRANSMIT_DUPLICATE);
        watch.setWatchEnabled(enabled);
        watch.setChangedReason(request.getReason());
        if (existed) {
            watchMapper.updateById(watch);
        } else {
            watchMapper.insert(watch);
        }
        evidenceService.record(SensorChangeEvidence.ENTITY_RISK_WATCH, watch.getId(),
                action, request.getRequestId(), before, watch,
                detailPrefix + "，原因=" + request.getReason());
        return watch;
    }

    private SensorRiskWatch copyOf(SensorRiskWatch source) {
        SensorRiskWatch copy = new SensorRiskWatch();
        copy.setId(source.getId());
        copy.setWatchSubject(source.getWatchSubject());
        copy.setWatchEnabled(source.getWatchEnabled());
        copy.setChangedReason(source.getChangedReason());
        return copy;
    }
}
