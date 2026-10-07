package com.evops.aqua.tide.service;

import com.evops.aqua.tide.entity.TideCrewShift;
import com.evops.aqua.tide.entity.TideWeatherBan;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 候选窗口评估器：叠加禁作天气与人员班次，判定候选是否可执行。
 * 统一口径（临界时间归属）全模块一致：
 *   重叠判定一律半开区间 [start, end)——start < 他.end 且 他.start < end，端点相接不算重叠；
 *   班次覆盖判定为闭区间——shift_start <= window_start 且 shift_end >= window_end，端点相等算覆盖；
 *   窗口归属日 = 窗口开始时间所在自然日（跨午夜归开始日）。
 */
@Component
public class TideWindowEvaluator {

    /** 半开区间重叠判定：端点相接（aEnd == bStart 或 bEnd == aStart）不算重叠。 */
    public boolean overlaps(LocalDateTime aStart, LocalDateTime aEnd,
                            LocalDateTime bStart, LocalDateTime bEnd) {
        return aStart.isBefore(bEnd) && bStart.isBefore(aEnd);
    }

    /** 候选窗口与禁作天气的冲突原因；无冲突返回空列表。 */
    public List<String> weatherConflicts(LocalDateTime start, LocalDateTime end, List<TideWeatherBan> bans) {
        List<String> reasons = new ArrayList<>();
        for (TideWeatherBan ban : bans) {
            if (overlaps(start, end, ban.getBanStart(), ban.getBanEnd())) {
                reasons.add("禁作天气[" + ban.getWeatherType() + "]" + ban.getBanStart()
                        + "~" + ban.getBanEnd() + "与候选窗口" + start + "~" + end + "重叠");
            }
        }
        return reasons;
    }

    /**
     * 全覆盖候选窗口的班组班次（闭区间，端点相等算覆盖）。
     * 多个班次可覆盖时取开始最早者，保证生成结果确定。
     */
    public TideCrewShift coveringShift(LocalDateTime start, LocalDateTime end, List<TideCrewShift> shifts) {
        return shifts.stream()
                .filter(s -> !s.getShiftStart().isAfter(start) && !s.getShiftEnd().isBefore(end))
                .min(Comparator.comparing(TideCrewShift::getShiftStart))
                .orElse(null);
    }
}
