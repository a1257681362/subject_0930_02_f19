package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.service.PondService;
import com.evops.aqua.tide.dto.PostponeRequest;
import com.evops.aqua.tide.dto.RescheduleRequest;
import com.evops.aqua.tide.dto.ScheduleCreateRequest;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideDevice;
import com.evops.aqua.tide.entity.TideSchedule;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.mapper.TideScheduleMapper;
import com.evops.aqua.tide.mapper.TideWindowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 排班：窗口 + 池塘 + 设备。同一设备同一时间只能服务一口池（半开区间判定，端点相接不算冲突）。
 * 发布后修改必须经调度审批（审批人与审批意见必填）；待发布排班可直接改期。
 * 自动顺延：系统挑选同海区下一个设备空闲的可执行窗口生成新排班（继承发布态），
 * 原计划行的时间/池塘/设备一律不变，仅状态置 POSTPONED——顺延不能改变原计划。
 */
@Service
public class TideScheduleService {

    private final TideScheduleMapper scheduleMapper;
    private final TideWindowMapper windowMapper;
    private final TideWindowService windowService;
    private final TideTableService tableService;
    private final PondService pondService;
    private final TideWindowEvaluator evaluator;
    private final TideEvidenceService evidenceService;

    public TideScheduleService(TideScheduleMapper scheduleMapper,
                               TideWindowMapper windowMapper,
                               TideWindowService windowService,
                               TideTableService tableService,
                               PondService pondService,
                               TideWindowEvaluator evaluator,
                               TideEvidenceService evidenceService) {
        this.scheduleMapper = scheduleMapper;
        this.windowMapper = windowMapper;
        this.windowService = windowService;
        this.tableService = tableService;
        this.pondService = pondService;
        this.evaluator = evaluator;
        this.evidenceService = evidenceService;
    }

    /**
     * 创建排班：校验窗口开放、池塘在养且海区一致、设备可用；
     * 同一池塘同一窗口只排一班；同一设备同一时间与既有排班重叠（半开口径）即拒绝。
     */
    @Transactional(rollbackFor = Exception.class)
    public TideSchedule create(ScheduleCreateRequest request) {
        TideSchedule duplicated = scheduleMapper.selectOne(
                new QueryWrapper<TideSchedule>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated; // 幂等：重复请求返回首次排班
        }
        TideWindow window = windowService.getOrThrow(request.getWindowId());
        requireWindowOpen(window);
        Pond pond = pondService.getOrThrow(request.getPondId());
        if (!Pond.STATUS_ACTIVE.equals(pond.getStatus())) {
            throw new IllegalArgumentException("池塘" + pond.getPondCode() + "已停用，不能排班");
        }
        if (!window.getSeaArea().equals(pond.getSeaArea())) {
            throw new IllegalArgumentException("池塘" + pond.getPondCode() + "属" + pond.getSeaArea()
                    + "，与窗口海区" + window.getSeaArea() + "不一致，不能排班");
        }
        TideDevice device = tableService.getDeviceOrThrow(request.getDeviceId());
        if (!TideDevice.STATUS_ACTIVE.equals(device.getStatus())) {
            throw new IllegalArgumentException("设备" + device.getDeviceCode() + "已停用，不能排班");
        }
        Long samePond = scheduleMapper.selectCount(new QueryWrapper<TideSchedule>()
                .eq("window_id", window.getId())
                .eq("pond_id", pond.getId())
                .in("status", TideSchedule.STATUS_PLANNED, TideSchedule.STATUS_PUBLISHED));
        if (samePond != null && samePond > 0) {
            throw new IllegalArgumentException("池塘" + pond.getPondCode()
                    + "在窗口" + window.getWindowNo() + "已有排班，同一池塘同一窗口只排一班");
        }
        requireDeviceFree(device, window.getWindowStart(), window.getWindowEnd(), null);

        TideSchedule schedule = new TideSchedule();
        schedule.setScheduleNo("TS-" + request.getRequestId());
        applyWindow(schedule, window);
        schedule.setPondId(pond.getId());
        schedule.setDeviceId(device.getId());
        schedule.setStatus(TideSchedule.STATUS_PLANNED);
        schedule.setRequestId(request.getRequestId());
        schedule.setRemark(request.getRemark());
        scheduleMapper.insert(schedule);

        evidenceService.record(TideChangeEvidence.ENTITY_SCHEDULE, schedule.getId(),
                TideChangeEvidence.ACTION_CREATE_SCHEDULE, request.getRequestId(), request.getOperator(),
                null, schedule,
                "创建排班：窗口" + window.getWindowNo() + "（" + window.getWindowStart() + "~"
                        + window.getWindowEnd() + "，归属日" + window.getWindowDate() + "），池塘"
                        + pond.getPondCode() + "，设备" + device.getDeviceCode()
                        + "，班组" + window.getCrewName() + "，操作者=" + request.getOperator());
        return schedule;
    }

    /** 发布排班：窗口须仍开放；重复发布幂等返回。 */
    @Transactional(rollbackFor = Exception.class)
    public TideSchedule publish(Long scheduleId, String operator) {
        TideSchedule schedule = getOrThrow(scheduleId);
        if (TideSchedule.STATUS_PUBLISHED.equals(schedule.getStatus())) {
            return schedule; // 幂等：重复发布返回同一排班
        }
        if (!TideSchedule.STATUS_PLANNED.equals(schedule.getStatus())) {
            throw new IllegalArgumentException("排班" + schedule.getScheduleNo()
                    + "当前状态" + schedule.getStatus() + "，不能发布");
        }
        TideWindow window = windowService.getOrThrow(schedule.getWindowId());
        requireWindowOpen(window);
        TideSchedule before = copyOf(schedule);
        schedule.setStatus(TideSchedule.STATUS_PUBLISHED);
        schedule.setPublishedBy(operator);
        schedule.setPublishedTime(LocalDateTime.now());
        scheduleMapper.updateById(schedule);

        evidenceService.record(TideChangeEvidence.ENTITY_SCHEDULE, schedule.getId(),
                TideChangeEvidence.ACTION_PUBLISH_SCHEDULE, null, operator,
                before, schedule,
                "发布排班" + schedule.getScheduleNo() + "：窗口" + window.getWindowNo()
                        + "，操作者=" + operator);
        return schedule;
    }

    /**
     * 改期：待发布排班直接改；已发布排班必须登记调度审批人与审批意见，否则拒绝。
     * 新窗口同样校验开放与设备冲突（排除自身）。同 requestId 幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public TideSchedule reschedule(Long scheduleId, RescheduleRequest request) {
        TideSchedule schedule = getOrThrow(scheduleId);
        if (evidenceService.findByActionAndRequest(
                TideChangeEvidence.ACTION_RESCHEDULE_SCHEDULE, request.getRequestId()) != null) {
            return schedule; // 幂等：同请求已改期过，返回当前状态
        }
        if (TideSchedule.STATUS_POSTPONED.equals(schedule.getStatus())) {
            throw new IllegalArgumentException("排班" + schedule.getScheduleNo()
                    + "已顺延，原计划冻结，不能再改期");
        }
        if (TideSchedule.STATUS_PUBLISHED.equals(schedule.getStatus())) {
            if (isBlank(request.getApprover()) || isBlank(request.getApproveRemark())) {
                throw new IllegalArgumentException("排班" + schedule.getScheduleNo()
                        + "已发布，修改必须经过调度审批：调度审批人与审批意见必填");
            }
        }
        TideWindow newWindow = windowService.getOrThrow(request.getNewWindowId());
        requireWindowOpen(newWindow);
        if (!newWindow.getSeaArea().equals(schedule.getSeaArea())) {
            throw new IllegalArgumentException("目标窗口海区" + newWindow.getSeaArea()
                    + "与排班海区" + schedule.getSeaArea() + "不一致，不能改期");
        }
        Long newDeviceId = request.getNewDeviceId() == null ? schedule.getDeviceId() : request.getNewDeviceId();
        TideDevice device = tableService.getDeviceOrThrow(newDeviceId);
        if (!TideDevice.STATUS_ACTIVE.equals(device.getStatus())) {
            throw new IllegalArgumentException("设备" + device.getDeviceCode() + "已停用，不能排班");
        }
        requireDeviceFree(device, newWindow.getWindowStart(), newWindow.getWindowEnd(), schedule.getId());

        TideSchedule before = copyOf(schedule);
        applyWindow(schedule, newWindow);
        schedule.setDeviceId(device.getId());
        scheduleMapper.updateById(schedule);

        String approval = TideSchedule.STATUS_PUBLISHED.equals(before.getStatus())
                ? "，调度审批人=" + request.getApprover() + "，审批意见[" + request.getApproveRemark() + "]"
                : "，待发布排班直接改期（无需审批）";
        evidenceService.record(TideChangeEvidence.ENTITY_SCHEDULE, schedule.getId(),
                TideChangeEvidence.ACTION_RESCHEDULE_SCHEDULE, request.getRequestId(), request.getOperator(),
                before, schedule,
                "排班改期：窗口" + before.getWindowId() + "->" + newWindow.getWindowNo()
                        + "（" + newWindow.getWindowStart() + "~" + newWindow.getWindowEnd()
                        + "，归属日" + newWindow.getWindowDate() + "），设备id=" + device.getId()
                        + approval + "，操作者=" + request.getOperator());
        return schedule;
    }

    /**
     * 自动顺延：系统挑选同海区下一个设备空闲的 OPEN 窗口（开始时间晚于原窗口），
     * 生成新排班行（origin 链接原计划，继承发布态）；原计划行仅状态置 POSTPONED，
     * 时间/池塘/设备一律不变——顺延不能改变原计划。requestId 幂等。
     */
    @Transactional(rollbackFor = Exception.class)
    public TideSchedule postpone(Long scheduleId, PostponeRequest request) {
        TideSchedule duplicated = scheduleMapper.selectOne(
                new QueryWrapper<TideSchedule>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated; // 幂等：重复顺延返回首次生成的顺延排班
        }
        TideSchedule origin = getOrThrow(scheduleId);
        if (!TideSchedule.STATUS_PUBLISHED.equals(origin.getStatus())) {
            throw new IllegalArgumentException("排班" + origin.getScheduleNo()
                    + "当前状态" + origin.getStatus() + "，仅已发布排班可自动顺延");
        }
        TideDevice device = tableService.getDeviceOrThrow(origin.getDeviceId());
        List<TideWindow> candidates = windowMapper.selectList(new QueryWrapper<TideWindow>()
                .eq("sea_area", origin.getSeaArea())
                .eq("status", TideWindow.STATUS_OPEN)
                .gt("window_start", origin.getSchedStart())
                .orderByAsc("window_start"));
        TideWindow target = null;
        for (TideWindow candidate : candidates) {
            if (deviceConflict(device.getId(), candidate.getWindowStart(), candidate.getWindowEnd(),
                    origin.getId()) == null) {
                target = candidate;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("排班" + origin.getScheduleNo()
                    + "暂无可顺延窗口（同海区更晚且设备" + device.getDeviceCode()
                    + "空闲的开放窗口不存在），原计划保持不变");
        }

        TideSchedule originBefore = copyOf(origin);
        origin.setStatus(TideSchedule.STATUS_POSTPONED); // 原计划仅状态变化，时间/池塘/设备不动
        scheduleMapper.updateById(origin);

        TideSchedule derived = new TideSchedule();
        derived.setScheduleNo("TS-" + request.getRequestId());
        applyWindow(derived, target);
        derived.setPondId(origin.getPondId());
        derived.setDeviceId(origin.getDeviceId());
        derived.setOriginScheduleId(origin.getId());
        derived.setStatus(TideSchedule.STATUS_PUBLISHED);
        derived.setPublishedBy(request.getOperator());
        derived.setPublishedTime(LocalDateTime.now());
        derived.setRequestId(request.getRequestId());
        derived.setRemark("自动顺延自排班" + origin.getScheduleNo()
                + (isBlank(request.getReason()) ? "" : "：" + request.getReason()));
        scheduleMapper.insert(derived);

        evidenceService.record(TideChangeEvidence.ENTITY_SCHEDULE, origin.getId(),
                TideChangeEvidence.ACTION_POSTPONE_SCHEDULE, request.getRequestId(), request.getOperator(),
                originBefore, origin,
                "自动顺延：原计划" + origin.getScheduleNo() + "（" + originBefore.getSchedStart() + "~"
                        + originBefore.getSchedEnd() + "，归属日" + originBefore.getSchedDate()
                        + "）保持不变仅置已顺延；新排班" + derived.getScheduleNo() + "顺延至窗口"
                        + target.getWindowNo() + "（" + target.getWindowStart() + "~" + target.getWindowEnd()
                        + "，归属日" + target.getWindowDate() + "），操作者=" + request.getOperator());
        return derived;
    }

    public TideSchedule getOrThrow(Long id) {
        TideSchedule schedule = scheduleMapper.selectById(id);
        if (schedule == null) {
            throw new IllegalArgumentException("排班不存在，id=" + id);
        }
        return schedule;
    }

    public List<TideSchedule> list(Long pondId, Long deviceId, Long windowId, String status) {
        QueryWrapper<TideSchedule> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.eq(windowId != null, "window_id", windowId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return scheduleMapper.selectList(wrapper);
    }

    private void requireWindowOpen(TideWindow window) {
        if (!TideWindow.STATUS_OPEN.equals(window.getStatus())) {
            throw new IllegalArgumentException("窗口" + window.getWindowNo() + "已临时关闭，不能排班/发布/改期");
        }
    }

    /** 设备占用校验：同一设备同一时间只能服务一口池；冲突时抛错并说明与哪条排班冲突。 */
    private void requireDeviceFree(TideDevice device, LocalDateTime start, LocalDateTime end, Long excludeId) {
        TideSchedule conflict = deviceConflict(device.getId(), start, end, excludeId);
        if (conflict != null) {
            throw new IllegalArgumentException("同一设备同一时间只能服务一口池：设备" + device.getDeviceCode()
                    + "在" + conflict.getSchedStart() + "~" + conflict.getSchedEnd()
                    + "已排班服务池塘id=" + conflict.getPondId()
                    + "（排班" + conflict.getScheduleNo() + "），与本次" + start + "~" + end + "重叠");
        }
    }

    /** 设备冲突检测：半开区间重叠判定（端点相接不算冲突），可排除指定排班自身。 */
    private TideSchedule deviceConflict(Long deviceId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        List<TideSchedule> occupied = scheduleMapper.selectList(new QueryWrapper<TideSchedule>()
                .eq("device_id", deviceId)
                .in("status", TideSchedule.STATUS_PLANNED, TideSchedule.STATUS_PUBLISHED));
        for (TideSchedule other : occupied) {
            if (excludeId != null && other.getId().equals(excludeId)) {
                continue;
            }
            if (evaluator.overlaps(start, end, other.getSchedStart(), other.getSchedEnd())) {
                return other;
            }
        }
        return null;
    }

    /** 把窗口的时间口径整体复制到排班：海区/班组/归属日/起止，归属日同一口径。 */
    private void applyWindow(TideSchedule schedule, TideWindow window) {
        schedule.setWindowId(window.getId());
        schedule.setSeaArea(window.getSeaArea());
        schedule.setCrewName(window.getCrewName());
        schedule.setSchedDate(window.getWindowDate());
        schedule.setSchedStart(window.getWindowStart());
        schedule.setSchedEnd(window.getWindowEnd());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private TideSchedule copyOf(TideSchedule source) {
        TideSchedule copy = new TideSchedule();
        copy.setId(source.getId());
        copy.setScheduleNo(source.getScheduleNo());
        copy.setWindowId(source.getWindowId());
        copy.setPondId(source.getPondId());
        copy.setDeviceId(source.getDeviceId());
        copy.setSeaArea(source.getSeaArea());
        copy.setCrewName(source.getCrewName());
        copy.setSchedDate(source.getSchedDate());
        copy.setSchedStart(source.getSchedStart());
        copy.setSchedEnd(source.getSchedEnd());
        copy.setOriginScheduleId(source.getOriginScheduleId());
        copy.setStatus(source.getStatus());
        copy.setPublishedBy(source.getPublishedBy());
        copy.setPublishedTime(source.getPublishedTime());
        copy.setRequestId(source.getRequestId());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
