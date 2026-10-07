package com.evops.aqua.sensor.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.sensor.dto.DeviceRegisterRequest;
import com.evops.aqua.sensor.dto.DeviceReplaceRequest;
import com.evops.aqua.sensor.entity.SensorChangeEvidence;
import com.evops.aqua.sensor.entity.SensorDevice;
import com.evops.aqua.sensor.entity.SensorDeviceSuccession;
import com.evops.aqua.sensor.mapper.SensorDeviceMapper;
import com.evops.aqua.sensor.mapper.SensorDeviceSuccessionMapper;
import com.evops.aqua.service.PondService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 传感器设备档案：登记（requestId 幂等）；更换建立新旧关联，
 * 旧设备退役、新设备接续同一池塘，关联链可双向追溯。
 */
@Service
public class SensorDeviceService {

    private final SensorDeviceMapper deviceMapper;
    private final SensorDeviceSuccessionMapper successionMapper;
    private final PondService pondService;
    private final SensorEvidenceService evidenceService;

    public SensorDeviceService(SensorDeviceMapper deviceMapper,
                               SensorDeviceSuccessionMapper successionMapper,
                               PondService pondService,
                               SensorEvidenceService evidenceService) {
        this.deviceMapper = deviceMapper;
        this.successionMapper = successionMapper;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    /**
     * 登记设备。幂等：相同 requestId 重复登记返回首次记录，不产生第二台设备。
     */
    @Transactional(rollbackFor = Exception.class)
    public SensorDevice register(DeviceRegisterRequest request) {
        List<SensorDevice> sameRequest = deviceMapper.selectList(
                new QueryWrapper<SensorDevice>().eq("request_id", request.getRequestId()));
        if (!sameRequest.isEmpty()) {
            return sameRequest.get(0);
        }
        pondService.getOrThrow(request.getPondId());
        Long codeExists = deviceMapper.selectCount(
                new QueryWrapper<SensorDevice>().eq("device_code", request.getDeviceCode()));
        if (codeExists != null && codeExists > 0) {
            throw new IllegalArgumentException("设备编码已存在：" + request.getDeviceCode());
        }
        SensorDevice device = new SensorDevice();
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setPondId(request.getPondId());
        device.setSecretKey(request.getSecretKey());
        device.setStatus(SensorDevice.STATUS_ACTIVE);
        device.setInstalledDate(request.getInstalledDate());
        device.setRequestId(request.getRequestId());
        device.setRemark(request.getRemark());
        deviceMapper.insert(device);
        evidenceService.record(SensorChangeEvidence.ENTITY_DEVICE, device.getId(),
                SensorChangeEvidence.ACTION_CREATE, request.getRequestId(), null, device,
                "登记传感器设备[" + device.getDeviceCode() + "]，绑定池塘id=" + device.getPondId());
        return device;
    }

    /**
     * 设备更换：同一事务内登记新设备、旧设备置 RETIRED、写新旧关联行。
     * 幂等：相同 requestId 重复请求返回首次更换结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> replace(Long oldDeviceId, DeviceReplaceRequest request) {
        List<SensorDeviceSuccession> sameRequest = successionMapper.selectList(
                new QueryWrapper<SensorDeviceSuccession>().eq("request_id", request.getRequestId()));
        if (!sameRequest.isEmpty()) {
            return replaceResult(sameRequest.get(0));
        }
        SensorDevice oldDevice = getOrThrow(oldDeviceId);
        if (!SensorDevice.STATUS_ACTIVE.equals(oldDevice.getStatus())) {
            throw new IllegalArgumentException("设备已退役，不能再次更换：" + oldDevice.getDeviceCode());
        }
        Long codeExists = deviceMapper.selectCount(
                new QueryWrapper<SensorDevice>().eq("device_code", request.getNewDeviceCode()));
        if (codeExists != null && codeExists > 0) {
            throw new IllegalArgumentException("新设备编码已存在：" + request.getNewDeviceCode());
        }

        SensorDevice newDevice = new SensorDevice();
        newDevice.setDeviceCode(request.getNewDeviceCode());
        newDevice.setDeviceName(request.getNewDeviceName());
        newDevice.setPondId(oldDevice.getPondId());
        newDevice.setSecretKey(request.getNewSecretKey());
        newDevice.setStatus(SensorDevice.STATUS_ACTIVE);
        newDevice.setInstalledDate(oldDevice.getInstalledDate());
        newDevice.setRemark("由设备[" + oldDevice.getDeviceCode() + "]更换接入");
        deviceMapper.insert(newDevice);

        SensorDevice beforeOld = copyOf(oldDevice);
        oldDevice.setStatus(SensorDevice.STATUS_RETIRED);
        deviceMapper.updateById(oldDevice);

        SensorDeviceSuccession succession = new SensorDeviceSuccession();
        succession.setPondId(oldDevice.getPondId());
        succession.setOldDeviceId(oldDevice.getId());
        succession.setNewDeviceId(newDevice.getId());
        succession.setReason(request.getReason());
        succession.setOperatorName(request.getOperatorName());
        succession.setReplacedTime(LocalDateTime.now());
        succession.setRequestId(request.getRequestId());
        successionMapper.insert(succession);

        evidenceService.record(SensorChangeEvidence.ENTITY_DEVICE, oldDevice.getId(),
                SensorChangeEvidence.ACTION_UPDATE, request.getRequestId(), beforeOld, oldDevice,
                "设备[" + oldDevice.getDeviceCode() + "]退役，由[" + newDevice.getDeviceCode() + "]接续");
        evidenceService.record(SensorChangeEvidence.ENTITY_DEVICE, newDevice.getId(),
                SensorChangeEvidence.ACTION_CREATE, request.getRequestId(), null, newDevice,
                "更换接入新设备[" + newDevice.getDeviceCode() + "]，接续池塘id=" + newDevice.getPondId());
        evidenceService.record(SensorChangeEvidence.ENTITY_SUCCESSION, succession.getId(),
                SensorChangeEvidence.ACTION_REPLACE, request.getRequestId(), null, succession,
                "建立新旧关联：[" + oldDevice.getDeviceCode() + "] → [" + newDevice.getDeviceCode()
                        + "]，原因：" + request.getReason() + "，操作人：" + request.getOperatorName());
        return replaceResult(succession);
    }

    /**
     * 设备谱系：沿新旧关联链向旧、向新双向追溯，返回按时间排列的完整链条。
     */
    public List<Map<String, Object>> lineage(Long deviceId) {
        SensorDevice device = getOrThrow(deviceId);
        List<SensorDevice> chain = new ArrayList<>();
        chain.add(device);
        // 向旧追溯
        SensorDevice cursor = device;
        while (true) {
            List<SensorDeviceSuccession> asNew = successionMapper.selectList(
                    new QueryWrapper<SensorDeviceSuccession>().eq("new_device_id", cursor.getId()));
            if (asNew.isEmpty()) {
                break;
            }
            cursor = getOrThrow(asNew.get(0).getOldDeviceId());
            chain.add(0, cursor);
        }
        // 向新追溯
        cursor = device;
        while (true) {
            List<SensorDeviceSuccession> asOld = successionMapper.selectList(
                    new QueryWrapper<SensorDeviceSuccession>().eq("old_device_id", cursor.getId()));
            if (asOld.isEmpty()) {
                break;
            }
            cursor = getOrThrow(asOld.get(0).getNewDeviceId());
            chain.add(cursor);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SensorDevice d : chain) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("deviceId", d.getId());
            node.put("deviceCode", d.getDeviceCode());
            node.put("deviceName", d.getDeviceName());
            node.put("pondId", d.getPondId());
            node.put("status", d.getStatus());
            result.add(node);
        }
        return result;
    }

    public SensorDevice getOrThrow(Long id) {
        SensorDevice device = deviceMapper.selectById(id);
        if (device == null) {
            throw new IllegalArgumentException("传感器设备不存在，id=" + id);
        }
        return device;
    }

    public SensorDevice findByCode(String deviceCode) {
        List<SensorDevice> list = deviceMapper.selectList(
                new QueryWrapper<SensorDevice>().eq("device_code", deviceCode));
        return list.isEmpty() ? null : list.get(0);
    }

    public List<SensorDevice> listDevices(Long pondId, String status) {
        QueryWrapper<SensorDevice> wrapper = new QueryWrapper<>();
        wrapper.eq(pondId != null, "pond_id", pondId);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return deviceMapper.selectList(wrapper);
    }

    private Map<String, Object> replaceResult(SensorDeviceSuccession succession) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("succession", succession);
        result.put("oldDevice", getOrThrow(succession.getOldDeviceId()));
        result.put("newDevice", getOrThrow(succession.getNewDeviceId()));
        return result;
    }

    private SensorDevice copyOf(SensorDevice source) {
        SensorDevice copy = new SensorDevice();
        copy.setId(source.getId());
        copy.setDeviceCode(source.getDeviceCode());
        copy.setDeviceName(source.getDeviceName());
        copy.setPondId(source.getPondId());
        copy.setSecretKey(source.getSecretKey());
        copy.setStatus(source.getStatus());
        copy.setInstalledDate(source.getInstalledDate());
        copy.setRequestId(source.getRequestId());
        copy.setRemark(source.getRemark());
        return copy;
    }
}
