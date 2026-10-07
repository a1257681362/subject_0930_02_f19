package com.evops.aqua.tide.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.tide.dto.DeviceRequest;
import com.evops.aqua.tide.dto.ShiftRequest;
import com.evops.aqua.tide.dto.TideEventRequest;
import com.evops.aqua.tide.dto.WeatherBanRequest;
import com.evops.aqua.tide.entity.TideChangeEvidence;
import com.evops.aqua.tide.entity.TideCrewShift;
import com.evops.aqua.tide.entity.TideDevice;
import com.evops.aqua.tide.entity.TideEvent;
import com.evops.aqua.tide.entity.TideWeatherBan;
import com.evops.aqua.tide.mapper.TideCrewShiftMapper;
import com.evops.aqua.tide.mapper.TideDeviceMapper;
import com.evops.aqua.tide.mapper.TideEventMapper;
import com.evops.aqua.tide.mapper.TideWeatherBanMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 潮汐基础数据：潮汐表、禁作天气、人员班次、换水设备的登记与查询。
 * 全部按 requestId 幂等；潮汐事件另有自然键（海区+时刻+潮型）幂等——
 * 同一条潮汐事实重复登记返回首次记录，不产生第二条。
 */
@Service
public class TideTableService {

    private final TideEventMapper eventMapper;
    private final TideWeatherBanMapper banMapper;
    private final TideCrewShiftMapper shiftMapper;
    private final TideDeviceMapper deviceMapper;
    private final TideEvidenceService evidenceService;

    public TideTableService(TideEventMapper eventMapper,
                            TideWeatherBanMapper banMapper,
                            TideCrewShiftMapper shiftMapper,
                            TideDeviceMapper deviceMapper,
                            TideEvidenceService evidenceService) {
        this.eventMapper = eventMapper;
        this.banMapper = banMapper;
        this.shiftMapper = shiftMapper;
        this.deviceMapper = deviceMapper;
        this.evidenceService = evidenceService;
    }

    /** 登记潮汐事件：requestId 幂等；同海区同时刻同潮型自然键幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public TideEvent registerEvent(TideEventRequest request) {
        TideEvent duplicated = eventMapper.selectOne(
                new QueryWrapper<TideEvent>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated; // 幂等：重复请求返回首次记录
        }
        TideEvent sameFact = eventMapper.selectOne(new QueryWrapper<TideEvent>()
                .eq("sea_area", request.getSeaArea())
                .eq("tide_time", request.getTideTime())
                .eq("tide_type", normalizeTideType(request.getTideType())));
        if (sameFact != null) {
            return sameFact; // 自然键幂等：同一条潮汐事实不重复落库
        }
        TideEvent event = new TideEvent();
        event.setSeaArea(request.getSeaArea());
        event.setTideTime(request.getTideTime());
        event.setTideType(normalizeTideType(request.getTideType()));
        event.setTideHeightCm(request.getTideHeightCm());
        event.setSource(request.getSource());
        event.setOperator(request.getOperator());
        event.setRequestId(request.getRequestId());
        event.setRemark(request.getRemark());
        eventMapper.insert(event);

        evidenceService.record(TideChangeEvidence.ENTITY_EVENT, event.getId(),
                TideChangeEvidence.ACTION_REGISTER_EVENT, request.getRequestId(), request.getOperator(),
                null, event,
                "登记潮汐事件：" + event.getSeaArea() + " " + event.getTideTime()
                        + " " + event.getTideType() + "，操作者=" + request.getOperator());
        return event;
    }

    /** 登记禁作天气：requestId 幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public TideWeatherBan registerBan(WeatherBanRequest request) {
        TideWeatherBan duplicated = banMapper.selectOne(
                new QueryWrapper<TideWeatherBan>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated;
        }
        if (!request.getBanStart().isBefore(request.getBanEnd())) {
            throw new IllegalArgumentException("禁作时段开始必须早于结束");
        }
        TideWeatherBan ban = new TideWeatherBan();
        ban.setSeaArea(request.getSeaArea());
        ban.setBanStart(request.getBanStart());
        ban.setBanEnd(request.getBanEnd());
        ban.setWeatherType(request.getWeatherType());
        ban.setReason(request.getReason());
        ban.setOperator(request.getOperator());
        ban.setRequestId(request.getRequestId());
        banMapper.insert(ban);

        evidenceService.record(TideChangeEvidence.ENTITY_BAN, ban.getId(),
                TideChangeEvidence.ACTION_REGISTER_BAN, request.getRequestId(), request.getOperator(),
                null, ban,
                "登记禁作天气：" + ban.getSeaArea() + " " + ban.getBanStart() + "~" + ban.getBanEnd()
                        + " [" + ban.getWeatherType() + "]，操作者=" + request.getOperator());
        return ban;
    }

    /** 登记人员班次：requestId 幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public TideCrewShift registerShift(ShiftRequest request) {
        TideCrewShift duplicated = shiftMapper.selectOne(
                new QueryWrapper<TideCrewShift>().eq("request_id", request.getRequestId()));
        if (duplicated != null) {
            return duplicated;
        }
        if (!request.getShiftStart().isBefore(request.getShiftEnd())) {
            throw new IllegalArgumentException("班次开始必须早于结束");
        }
        TideCrewShift shift = new TideCrewShift();
        shift.setCrewName(request.getCrewName());
        shift.setShiftStart(request.getShiftStart());
        shift.setShiftEnd(request.getShiftEnd());
        shift.setOperator(request.getOperator());
        shift.setRequestId(request.getRequestId());
        shift.setRemark(request.getRemark());
        shiftMapper.insert(shift);

        evidenceService.record(TideChangeEvidence.ENTITY_SHIFT, shift.getId(),
                TideChangeEvidence.ACTION_REGISTER_SHIFT, request.getRequestId(), request.getOperator(),
                null, shift,
                "登记人员班次：班组" + shift.getCrewName() + " " + shift.getShiftStart()
                        + "~" + shift.getShiftEnd() + "，操作者=" + request.getOperator());
        return shift;
    }

    /** 登记换水设备：设备编号唯一，重复登记同编号返回已有设备。 */
    @Transactional(rollbackFor = Exception.class)
    public TideDevice registerDevice(DeviceRequest request) {
        TideDevice existing = deviceMapper.selectOne(
                new QueryWrapper<TideDevice>().eq("device_code", request.getDeviceCode()));
        if (existing != null) {
            return existing; // 设备编号自然键幂等
        }
        TideDevice device = new TideDevice();
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setSeaArea(request.getSeaArea());
        device.setStatus(TideDevice.STATUS_ACTIVE);
        device.setOperator(request.getOperator());
        device.setRemark(request.getRemark());
        deviceMapper.insert(device);

        evidenceService.record(TideChangeEvidence.ENTITY_DEVICE, device.getId(),
                TideChangeEvidence.ACTION_REGISTER_DEVICE, null, request.getOperator(),
                null, device,
                "登记换水设备：" + device.getDeviceCode() + "（" + device.getDeviceName()
                        + "），海区" + device.getSeaArea() + "，操作者=" + request.getOperator());
        return device;
    }

    public TideDevice getDeviceOrThrow(Long id) {
        TideDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("换水设备不存在，id=" + id);
        }
        return device;
    }

    public List<TideEvent> listEvents(String seaArea) {
        QueryWrapper<TideEvent> wrapper = new QueryWrapper<>();
        wrapper.eq(seaArea != null && !seaArea.trim().isEmpty(), "sea_area", seaArea);
        wrapper.orderByAsc("tide_time");
        return eventMapper.selectList(wrapper);
    }

    public List<TideWeatherBan> listBans(String seaArea) {
        QueryWrapper<TideWeatherBan> wrapper = new QueryWrapper<>();
        wrapper.eq(seaArea != null && !seaArea.trim().isEmpty(), "sea_area", seaArea);
        wrapper.orderByAsc("ban_start");
        return banMapper.selectList(wrapper);
    }

    public List<TideCrewShift> listShifts(String crewName) {
        QueryWrapper<TideCrewShift> wrapper = new QueryWrapper<>();
        wrapper.eq(crewName != null && !crewName.trim().isEmpty(), "crew_name", crewName);
        wrapper.orderByAsc("shift_start");
        return shiftMapper.selectList(wrapper);
    }

    public List<TideDevice> listDevices(String seaArea) {
        QueryWrapper<TideDevice> wrapper = new QueryWrapper<>();
        wrapper.eq(seaArea != null && !seaArea.trim().isEmpty(), "sea_area", seaArea);
        wrapper.orderByAsc("id");
        return deviceMapper.selectList(wrapper);
    }

    private String normalizeTideType(String tideType) {
        String type = tideType == null ? "" : tideType.trim().toUpperCase();
        if (!TideEvent.TYPE_HIGH.equals(type) && !TideEvent.TYPE_LOW.equals(type)) {
            throw new IllegalArgumentException("潮型只支持 HIGH/LOW，收到：" + tideType);
        }
        return type;
    }
}
