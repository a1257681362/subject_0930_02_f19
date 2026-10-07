package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.MaintDeviceRegisterRequest;
import com.evops.aqua.maint.dto.MeterReadingRequest;
import com.evops.aqua.maint.entity.MaintChangeEvidence;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintMeterReading;
import com.evops.aqua.maint.mapper.MaintDeviceMapper;
import com.evops.aqua.maint.mapper.MaintMeterReadingMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 设备档案与运行小时账：设备登记、累计运行小时上报（只追加，读数不得回退，并同步设备最新读数）。
 */
@Service
public class MaintDeviceService {

    public static final String DEVICE_TYPES = "AERATOR/PUMP_STATION/FEEDER";

    private final MaintDeviceMapper deviceMapper;
    private final MaintMeterReadingMapper readingMapper;
    private final MaintEvidenceService evidenceService;

    public MaintDeviceService(MaintDeviceMapper deviceMapper,
                              MaintMeterReadingMapper readingMapper,
                              MaintEvidenceService evidenceService) {
        this.deviceMapper = deviceMapper;
        this.readingMapper = readingMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintDevice register(MaintDeviceRegisterRequest request) {
        Long duplicated = deviceMapper.selectCount(
                new QueryWrapper<MaintDevice>().eq("device_code", request.getDeviceCode()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("设备编码已存在：" + request.getDeviceCode());
        }
        String type = normalizeType(request.getDeviceType());
        MaintDevice device = new MaintDevice();
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setDeviceType(type);
        device.setPondId(request.getPondId());
        device.setSeaArea(request.getSeaArea());
        device.setRunningHours(BigDecimal.ZERO);
        device.setStatus(MaintDevice.STATUS_RUNNING);
        device.setOperator(request.getOperator());
        device.setRemark(request.getRemark());
        deviceMapper.insert(device);

        evidenceService.record(MaintChangeEvidence.ENTITY_DEVICE, device.getId(),
                MaintChangeEvidence.ACTION_REGISTER_DEVICE, null, request.getOperator(),
                null, device, "登记设备[" + device.getDeviceCode() + "]，类型=" + type);
        return device;
    }

    /**
     * 运行小时上报：只追加一行；累计读数不得小于设备最新读数（回退拒绝）。
     * requestId 幂等：重复上报返回首次记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public MaintMeterReading reportReading(Long deviceId, MeterReadingRequest request) {
        MaintDevice device = getOrThrow(deviceId);
        MaintMeterReading existing = readingMapper.selectOne(
                new QueryWrapper<MaintMeterReading>().eq("request_id", request.getRequestId()));
        if (existing != null) {
            return existing;
        }
        if (request.getReadingHours().compareTo(device.getRunningHours()) < 0) {
            throw new IllegalArgumentException("设备[" + device.getDeviceCode() + "]累计运行小时不能回退：最新读数="
                    + device.getRunningHours() + "，上报=" + request.getReadingHours());
        }
        MaintMeterReading reading = new MaintMeterReading();
        reading.setDeviceId(deviceId);
        reading.setReadingHours(request.getReadingHours());
        reading.setReadingDate(request.getReadingDate());
        reading.setOperator(request.getOperator());
        reading.setRequestId(request.getRequestId());
        reading.setRemark(request.getRemark());
        readingMapper.insert(reading);

        BigDecimal before = device.getRunningHours();
        device.setRunningHours(request.getReadingHours());
        deviceMapper.updateById(device);

        evidenceService.record(MaintChangeEvidence.ENTITY_DEVICE, deviceId,
                MaintChangeEvidence.ACTION_METER_READING, request.getRequestId(), request.getOperator(),
                before, request.getReadingHours(),
                "设备[" + device.getDeviceCode() + "]运行小时上报：" + before + " -> " + request.getReadingHours());
        return reading;
    }

    public MaintDevice getOrThrow(Long id) {
        MaintDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("设备不存在，id=" + id);
        }
        return device;
    }

    public List<MaintDevice> list(String deviceType, String status) {
        QueryWrapper<MaintDevice> wrapper = new QueryWrapper<>();
        wrapper.eq(deviceType != null && !deviceType.trim().isEmpty(), "device_type", deviceType);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return deviceMapper.selectList(wrapper);
    }

    public static String normalizeType(String type) {
        if (type == null) {
            throw new IllegalArgumentException("设备类型不能为空：" + DEVICE_TYPES);
        }
        String normalized = type.trim().toUpperCase();
        if (!MaintDevice.TYPE_AERATOR.equals(normalized)
                && !MaintDevice.TYPE_PUMP_STATION.equals(normalized)
                && !MaintDevice.TYPE_FEEDER.equals(normalized)) {
            throw new IllegalArgumentException("设备类型只支持 " + DEVICE_TYPES + "，收到：" + type);
        }
        return normalized;
    }
}
