package com.evops.aqua.maint.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.maint.dto.WorkerRegisterRequest;
import com.evops.aqua.maint.entity.MaintDevice;
import com.evops.aqua.maint.entity.MaintWorker;
import com.evops.aqua.maint.mapper.MaintWorkerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 维修人员档案与资质校验：派工对象必须在职且资质覆盖设备类型。
 */
@Service
public class WorkerService {

    private final MaintWorkerMapper workerMapper;

    public WorkerService(MaintWorkerMapper workerMapper) {
        this.workerMapper = workerMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public MaintWorker register(WorkerRegisterRequest request) {
        Long duplicated = workerMapper.selectCount(
                new QueryWrapper<MaintWorker>().eq("worker_code", request.getWorkerCode()));
        if (duplicated != null && duplicated > 0) {
            throw new IllegalArgumentException("维修工号已存在：" + request.getWorkerCode());
        }
        MaintWorker worker = new MaintWorker();
        worker.setWorkerCode(request.getWorkerCode());
        worker.setWorkerName(request.getWorkerName());
        worker.setQualifiedTypes(normalizeTypes(request.getQualifiedTypes()));
        worker.setPhone(request.getPhone());
        worker.setStatus(MaintWorker.STATUS_ACTIVE);
        workerMapper.insert(worker);
        return worker;
    }

    public MaintWorker getOrThrow(Long id) {
        MaintWorker worker = workerMapper.selectById(id);
        if (worker == null) {
            throw new IllegalArgumentException("维修人员不存在，id=" + id);
        }
        return worker;
    }

    public List<MaintWorker> listAll() {
        return workerMapper.selectList(new QueryWrapper<MaintWorker>().orderByAsc("id"));
    }

    /**
     * 逐个校验派工人员：去重、在职、资质覆盖设备类型。任一不满足一次性列出全部冲突。
     */
    public List<MaintWorker> requireAssignable(List<Long> workerIds, MaintDevice device) {
        if (workerIds == null || workerIds.isEmpty()) {
            throw new IllegalArgumentException("至少派一名维修人员");
        }
        List<String> conflicts = new ArrayList<>();
        List<MaintWorker> workers = new ArrayList<>();
        List<Long> distinct = new ArrayList<>();
        for (Long id : workerIds) {
            if (!distinct.contains(id)) {
                distinct.add(id);
            }
        }
        for (Long id : distinct) {
            MaintWorker worker = getOrThrow(id);
            if (!MaintWorker.STATUS_ACTIVE.equals(worker.getStatus())) {
                conflicts.add("人员[" + worker.getWorkerName() + "]已停用");
            }
            if (!qualified(worker, device.getDeviceType())) {
                conflicts.add("人员[" + worker.getWorkerName() + "]无设备类型" + device.getDeviceType()
                        + "的维修资质（资质=" + worker.getQualifiedTypes() + "）");
            }
            workers.add(worker);
        }
        if (!conflicts.isEmpty()) {
            throw new IllegalArgumentException("派工校验失败：" + String.join("；", conflicts));
        }
        return workers;
    }

    static boolean qualified(MaintWorker worker, String deviceType) {
        if (worker.getQualifiedTypes() == null || worker.getQualifiedTypes().trim().isEmpty()) {
            return false;
        }
        for (String type : Arrays.asList(worker.getQualifiedTypes().split(","))) {
            if (deviceType.equals(type.trim().toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeTypes(String types) {
        if (types == null || types.trim().isEmpty()) {
            throw new IllegalArgumentException("维修资质类型不能为空（AERATOR/PUMP_STATION/FEEDER 逗号分隔）");
        }
        List<String> normalized = new ArrayList<>();
        for (String type : Arrays.asList(types.split(","))) {
            String t = MaintDeviceService.normalizeType(type.trim());
            if (!normalized.contains(t)) {
                normalized.add(t);
            }
        }
        return String.join(",", normalized);
    }
}
