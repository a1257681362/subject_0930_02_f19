package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.dto.CloseWindowRequest;
import com.evops.aqua.tide.dto.WindowGenerateRequest;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideCrewShift;
import com.evops.aqua.tide.entity.TideEvent;
import com.evops.aqua.tide.entity.TideNotification;
import com.evops.aqua.tide.entity.TideSchedule;
import com.evops.aqua.tide.entity.TideWeatherBan;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.mapper.TideCrewShiftMapper;
import com.evops.aqua.tide.mapper.TideEventMapper;
import com.evops.aqua.tide.mapper.TideNotificationMapper;
import com.evops.aqua.tide.mapper.TideScheduleMapper;
import com.evops.aqua.tide.mapper.TideWeatherBanMapper;
import com.evops.aqua.tide.mapper.TideWindowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 换水窗口：按海区潮汐表生成候选 → 叠加禁作天气与人员班次 → 可执行窗口。
 * 生成幂等：同 requestId 返回首次结果；同一潮汐事件只生成一个窗口。
 * 临时关闭：窗口置 CLOSED 并向窗口内受影响班组（待发布/已发布排班）逐条发通知，
 * 通知待班组签收留回执；重复关闭不多发通知。
 */
@Service
public class TideWindowService {

    private static final int DEFAULT_MARGIN_MINUTES = 60;

    private final TideWindowMapper windowMapper;
    private final TideEventMapper eventMapper;
    private final TideWeatherBanMapper banMapper;
    private final TideCrewShiftMapper shiftMapper;
    private final TideScheduleMapper scheduleMapper;
    private final TideNotificationMapper notificationMapper;
    private final TideWindowEvaluator evaluator;
    private final TideEvidenceService evidenceService;

    public TideWindowService(TideWindowMapper windowMapper,
                             TideEventMapper eventMapper,
                             TideWeatherBanMapper banMapper,
                             TideCrewShiftMapper shiftMapper,
                             TideScheduleMapper scheduleMapper,
                             TideNotificationMapper notificationMapper,
                             TideWindowEvaluator evaluator,
                             TideEvidenceService evidenceService) {
        this.windowMapper = windowMapper;
        this.eventMapper = eventMapper;
        this.banMapper = banMapper;
        this.shiftMapper = shiftMapper;
        this.scheduleMapper = scheduleMapper;
        this.notificationMapper = notificationMapper;
        this.evaluator = evaluator;
        this.evidenceService = evidenceService;
    }

    /**
     * 生成可执行窗口：取该海区当日全部高潮事件，向前后各推 margin 分钟得候选时段，
     * 剔除与禁作天气重叠（半开口径）的候选，要求有班组班次全覆盖（闭区间口径）。
     * 归属日 = 候选开始时间所在自然日——跨午夜候选归开始日，与查询/排班/通知同一口径。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generate(WindowGenerateRequest request) {
        List<TideWindow> duplicated = windowMapper.selectList(
                new QueryWrapper<TideWindow>().eq("request_id", request.getRequestId()));
        if (!duplicated.isEmpty()) {
            return result(duplicated, new ArrayList<>()); // 幂等：重复生成返回首次结果
        }
        int margin = request.getMarginMinutes() == null ? DEFAULT_MARGIN_MINUTES : request.getMarginMinutes();
        if (margin <= 0) {
            throw new IllegalArgumentException("窗口前后推分钟数必须为正数");
        }
        LocalDateTime dayStart = request.getDate().atStartOfDay();
        LocalDateTime dayEnd = request.getDate().plusDays(1).atStartOfDay();
        List<TideEvent> highs = eventMapper.selectList(new QueryWrapper<TideEvent>()
                .eq("sea_area", request.getSeaArea())
                .eq("tide_type", TideEvent.TYPE_HIGH)
                .ge("tide_time", dayStart)
                .lt("tide_time", dayEnd)
                .orderByAsc("tide_time"));
        List<TideWeatherBan> bans = banMapper.selectList(
                new QueryWrapper<TideWeatherBan>().eq("sea_area", request.getSeaArea()));
        List<TideCrewShift> shifts = shiftMapper.selectList(new QueryWrapper<>());

        List<TideWindow> windows = new ArrayList<>();
        List<Map<String, Object>> excluded = new ArrayList<>();
        for (TideEvent event : highs) {
            TideWindow existing = windowMapper.selectOne(
                    new QueryWrapper<TideWindow>().eq("tide_event_id", event.getId()));
            if (existing != null) {
                windows.add(existing); // 同一潮汐事件只生成一个窗口，重复生成不多写
                continue;
            }
            LocalDateTime start = event.getTideTime().minusMinutes(margin);
            LocalDateTime end = event.getTideTime().plusMinutes(margin);
            List<String> reasons = evaluator.weatherConflicts(start, end, bans);
            TideCrewShift cover = evaluator.coveringShift(start, end, shifts);
            if (cover == null) {
                reasons.add("无班组班次覆盖候选窗口" + start + "~" + end);
            }
            if (!reasons.isEmpty()) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("tideEventId", event.getId());
                item.put("tideTime", event.getTideTime());
                item.put("candidateStart", start);
                item.put("candidateEnd", end);
                item.put("reasons", reasons);
                excluded.add(item);
                continue;
            }
            TideWindow window = new TideWindow();
            window.setWindowNo("TW-E" + event.getId());
            window.setSeaArea(request.getSeaArea());
            window.setTideEventId(event.getId());
            window.setCrewName(cover.getCrewName());
            // 统一口径：归属日 = 窗口开始时间所在自然日（跨午夜归开始日）
            window.setWindowDate(start.toLocalDate());
            window.setWindowStart(start);
            window.setWindowEnd(end);
            window.setStatus(TideWindow.STATUS_OPEN);
            window.setRequestId(request.getRequestId());
            windowMapper.insert(window);
            windows.add(window);

            evidenceService.record(TideChangeEvidence.ENTITY_WINDOW, window.getId(),
                    TideChangeEvidence.ACTION_GENERATE_WINDOW, request.getRequestId(), request.getOperator(),
                    null, window,
                    "生成可执行窗口：" + window.getWindowNo() + " " + start + "~" + end
                            + "，归属日" + window.getWindowDate() + "，当班班组" + cover.getCrewName()
                            + "，操作者=" + request.getOperator());
        }
        return result(windows, excluded);
    }

    /**
     * 临时关闭窗口：登记关闭人与原因，窗口置 CLOSED；
     * 窗口内待发布/已发布排班的班组为受影响班组，逐条发送通知（待签收回执）。
     * 幂等：窗口已关闭或同 requestId 已处理时返回已有通知，不重复发送。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> close(Long windowId, CloseWindowRequest request) {
        TideWindow window = getOrThrow(windowId);
        if (TideWindow.STATUS_CLOSED.equals(window.getStatus())) {
            return closeResult(window, notificationsOf(windowId)); // 幂等：重复关闭不多发通知
        }
        List<TideNotification> sameRequest = notificationMapper.selectList(
                new QueryWrapper<TideNotification>().eq("request_id", request.getRequestId()));
        if (!sameRequest.isEmpty()) {
            return closeResult(window, sameRequest);
        }
        TideWindow before = copyOf(window);
        window.setStatus(TideWindow.STATUS_CLOSED);
        window.setClosedBy(request.getOperator());
        window.setClosedTime(LocalDateTime.now());
        window.setClosedReason(request.getReason());
        windowMapper.updateById(window);

        List<TideSchedule> affected = scheduleMapper.selectList(new QueryWrapper<TideSchedule>()
                .eq("window_id", windowId)
                .in("status", TideSchedule.STATUS_PLANNED, TideSchedule.STATUS_PUBLISHED)
                .orderByAsc("id"));
        List<TideNotification> notifications = new ArrayList<>();
        for (TideSchedule schedule : affected) {
            TideNotification notification = new TideNotification();
            notification.setNoticeNo("TN-W" + windowId + "-S" + schedule.getId());
            notification.setWindowId(windowId);
            notification.setScheduleId(schedule.getId());
            notification.setCrewName(schedule.getCrewName());
            notification.setMessage("换水窗口" + window.getWindowNo() + "（" + window.getWindowStart()
                    + "~" + window.getWindowEnd() + "，归属日" + window.getWindowDate()
                    + "）临时关闭：" + request.getReason() + "。请班组「" + schedule.getCrewName()
                    + "」签收确认，池塘id=" + schedule.getPondId() + "，排班" + schedule.getScheduleNo());
            notification.setStatus(TideNotification.STATUS_SENT);
            notification.setRequestId(request.getRequestId());
            notificationMapper.insert(notification);
            notifications.add(notification);

            evidenceService.record(TideChangeEvidence.ENTITY_NOTIFICATION, notification.getId(),
                    TideChangeEvidence.ACTION_NOTIFY_CREW, request.getRequestId(), request.getOperator(),
                    null, notification,
                    "窗口临时关闭通知班组" + schedule.getCrewName() + "：排班" + schedule.getScheduleNo()
                            + "（池塘id=" + schedule.getPondId() + "），操作者=" + request.getOperator());
        }
        evidenceService.record(TideChangeEvidence.ENTITY_WINDOW, window.getId(),
                TideChangeEvidence.ACTION_CLOSE_WINDOW, request.getRequestId(), request.getOperator(),
                before, window,
                "临时关闭窗口" + window.getWindowNo() + "：" + request.getReason()
                        + "，受影响排班" + notifications.size() + "条已通知班组，操作者=" + request.getOperator());
        return closeResult(window, notifications);
    }

    public TideWindow getOrThrow(Long id) {
        TideWindow window = windowMapper.selectById(id);
        if (window == null) {
            throw new IllegalArgumentException("换水窗口不存在，id=" + id);
        }
        return window;
    }

    /** 窗口查询：按海区与归属日（统一口径：归属日 = 窗口开始时间所在自然日）。 */
    public List<TideWindow> list(String seaArea, LocalDate date, String status) {
        QueryWrapper<TideWindow> wrapper = new QueryWrapper<>();
        wrapper.eq(seaArea != null && !seaArea.trim().isEmpty(), "sea_area", seaArea);
        wrapper.eq(date != null, "window_date", date);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("window_start");
        return windowMapper.selectList(wrapper);
    }

    public List<TideNotification> notificationsOf(Long windowId) {
        return notificationMapper.selectList(new QueryWrapper<TideNotification>()
                .eq("window_id", windowId).orderByAsc("id"));
    }

    private Map<String, Object> result(List<TideWindow> windows, List<Map<String, Object>> excluded) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windows", windows);
        result.put("excluded", excluded);
        return result;
    }

    private Map<String, Object> closeResult(TideWindow window, List<TideNotification> notifications) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("window", window);
        result.put("notifications", notifications);
        return result;
    }

    private TideWindow copyOf(TideWindow source) {
        TideWindow copy = new TideWindow();
        copy.setId(source.getId());
        copy.setWindowNo(source.getWindowNo());
        copy.setSeaArea(source.getSeaArea());
        copy.setTideEventId(source.getTideEventId());
        copy.setCrewName(source.getCrewName());
        copy.setWindowDate(source.getWindowDate());
        copy.setWindowStart(source.getWindowStart());
        copy.setWindowEnd(source.getWindowEnd());
        copy.setStatus(source.getStatus());
        copy.setClosedBy(source.getClosedBy());
        copy.setClosedTime(source.getClosedTime());
        copy.setClosedReason(source.getClosedReason());
        copy.setRequestId(source.getRequestId());
        return copy;
    }
}
