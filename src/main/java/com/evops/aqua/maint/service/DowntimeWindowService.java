package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.DowntimeWindowRequest;
import com.evops.aqua.maint.entity.DowntimeWindow;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.mapper.DowntimeWindowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 停机窗口：设备允许停机的半开区间 [start, end)。
 * 同设备开放窗口之间不得半开重叠；工单计划时段必须被某个开放窗口完整覆盖。
 */
@Service
public class DowntimeWindowService {

    private final DowntimeWindowMapper windowMapper;
    private final MaintDeviceService deviceService;
    private final MaintEvidenceService evidenceService;

    public DowntimeWindowService(DowntimeWindowMapper windowMapper,
                                 MaintDeviceService deviceService,
                                 MaintEvidenceService evidenceService) {
        this.windowMapper = windowMapper;
        this.deviceService = deviceService;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public DowntimeWindow register(Long deviceId, DowntimeWindowRequest request) {
        MaintDevice device = deviceService.getOrThrow(deviceId);
        DowntimeWindow existing = windowMapper.selectOne(
                new QueryWrapper<DowntimeWindow>().eq("request_id", request.getRequestId()));
        if (existing != null) {
            return existing;
        }
        validateRange(request.getWindowStart(), request.getWindowEnd());
        List<DowntimeWindow> sameDevice = windowMapper.selectList(
                new QueryWrapper<DowntimeWindow>()
                        .eq("device_id", deviceId)
                        .eq("status", DowntimeWindow.STATUS_OPEN));
        for (DowntimeWindow other : sameDevice) {
            if (overlaps(request.getWindowStart(), request.getWindowEnd(),
                    other.getWindowStart(), other.getWindowEnd())) {
                throw new IllegalArgumentException("设备[" + device.getDeviceCode()
                        + "]停机窗口与已开放窗口[" + other.getWindowNo() + "]时段重叠（按半开区间[start,end)判定）");
            }
        }

        DowntimeWindow window = new DowntimeWindow();
        window.setWindowNo(nextWindowNo(deviceId));
        window.setDeviceId(deviceId);
        window.setWindowStart(request.getWindowStart());
        window.setWindowEnd(request.getWindowEnd());
        window.setReason(request.getReason());
        window.setStatus(DowntimeWindow.STATUS_OPEN);
        window.setRegisteredBy(request.getRegisteredBy());
        window.setRequestId(request.getRequestId());
        windowMapper.insert(window);

        evidenceService.record(MaintChangeEvidence.ENTITY_WINDOW, window.getId(),
                MaintChangeEvidence.ACTION_REGISTER_WINDOW, request.getRequestId(), request.getRegisteredBy(),
                null, window, "设备[" + device.getDeviceCode() + "]登记停机窗口" + window.getWindowNo()
                        + "：" + window.getWindowStart() + " ~ " + window.getWindowEnd());
        return window;
    }

    /**
     * 校验设备存在覆盖 [start, end) 的开放窗口；返回命中的窗口。
     * 端点相接按半开区间允许：start==window.end 不算越界。
     */
    public DowntimeWindow requireCoveringWindow(Long deviceId, LocalDateTime start, LocalDateTime end) {
        validateRange(start, end);
        List<DowntimeWindow> windows = windowMapper.selectList(
                new QueryWrapper<DowntimeWindow>()
                        .eq("device_id", deviceId)
                        .eq("status", DowntimeWindow.STATUS_OPEN)
                        .orderByAsc("id"));
        for (DowntimeWindow window : windows) {
            if (!start.isBefore(window.getWindowStart()) && !end.isAfter(window.getWindowEnd())) {
                return window;
            }
        }
        throw new IllegalArgumentException("设备id=" + deviceId + "在时段 " + start + " ~ " + end
                + " 没有覆盖该时段的开放停机窗口，不能安排保养工单");
    }

    public DowntimeWindow getOrThrow(Long id) {
        DowntimeWindow window = windowMapper.selectById(id);
        if (window == null) {
            throw new IllegalArgumentException("停机窗口不存在，id=" + id);
        }
        return window;
    }

    public List<DowntimeWindow> list(Long deviceId, String status) {
        QueryWrapper<DowntimeWindow> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceId != null, "device_id", deviceId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("window_start");
        return windowMapper.selectList(wrapper);
    }

    static void validateRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new IllegalArgumentException("时段开始必须早于结束（按半开区间[start,end)）");
        }
    }

    /** 半开区间 [s1,e1) 与 [s2,e2) 是否重叠：端点相接不算重叠。 */
    static boolean overlaps(LocalDateTime s1, LocalDateTime e1, LocalDateTime s2, LocalDateTime e2) {
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    private String nextWindowNo(Long deviceId) {
        Long count = windowMapper.selectCount(
                new QueryWrapper<DowntimeWindow>().eq("device_id", deviceId));
        long seq = (count == null ? 0 : count) + 1;
        return "DW-" + deviceId + "-" + seq;
    }
}
