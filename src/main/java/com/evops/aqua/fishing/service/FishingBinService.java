package com.evops.aqua.fishing.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fishing.dto.BinInspectRequest;
import com.evops.aqua.fishing.dto.BinRegisterRequest;
import com.evops.aqua.fishing.entity.FishingBin;
import com.evops.aqua.fishing.entity.FishingChangeEvidence;
import com.evops.aqua.fishing.mapper.FishingBinMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 库位（分仓）：登记按规格建仓；检验时库位状态与待检标识同一事务同步复位。
 * 占用约束：库位被某订单占用期间，其他订单的预约不得锁定或入仓。
 */
@Service
public class FishingBinService {

    private final FishingBinMapper binMapper;
    private final FishingEvidenceService evidenceService;

    public FishingBinService(FishingBinMapper binMapper, FishingEvidenceService evidenceService) {
        this.binMapper = binMapper;
        this.evidenceService = evidenceService;
    }

    /**
     * 登记库位：requestId 幂等，重复登记返回首次记录；binNo 全局唯一。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingBin register(BinRegisterRequest request) {
        FishingBin duplicated = binMapper.selectOne(new QueryWrapper<FishingBin>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        FishingBin sameNo = binMapper.selectOne(new QueryWrapper<FishingBin>()
                .eq("bin_no", request.getBinNo()).last("limit 1"));
        if (sameNo != null) {
            throw new IllegalArgumentException("库位编号" + request.getBinNo() + "已存在（规格"
                    + sameNo.getSpecCode() + "），请更换编号");
        }
        if (request.getCapacityKg() == null || request.getCapacityKg().signum() <= 0) {
            throw new IllegalArgumentException("库位容量必须大于0");
        }
        FishingBin bin = new FishingBin();
        bin.setBinNo(request.getBinNo());
        bin.setSpecCode(request.getSpecCode());
        bin.setCapacityKg(request.getCapacityKg());
        bin.setLoadedKg(java.math.BigDecimal.ZERO);
        bin.setStatus(FishingBin.STATUS_EMPTY);
        bin.setPendingInspection(false);
        bin.setOperator(request.getOperator());
        bin.setRequestId(request.getRequestId());
        bin.setRemark(request.getRemark());
        binMapper.insert(bin);
        evidenceService.record(FishingChangeEvidence.ENTITY_BIN, bin.getId(),
                FishingChangeEvidence.ACTION_BIN_REGISTER, request.getRequestId(), request.getOperator(),
                null, bin, "登记库位" + bin.getBinNo() + "（规格" + bin.getSpecCode() + "，容量" + bin.getCapacityKg() + "kg）");
        return bin;
    }

    /**
     * 检验完成：待检标识清除、状态同步置 CLEARED——两者同一事务一次更新，不出现中间态。
     * 非待检状态重复检验幂等返回当前库位。
     */
    @Transactional(rollbackFor = Exception.class)
    public FishingBin inspect(Long id, BinInspectRequest request) {
        FishingBin bin = getOrThrow(id);
        if (!Boolean.TRUE.equals(bin.getPendingInspection())) {
            return bin;
        }
        FishingBin before = copyOf(bin);
        bin.setPendingInspection(false);
        bin.setStatus(FishingBin.STATUS_CLEARED);
        bin.setInspectedBy(request.getOperator());
        bin.setInspectedTime(LocalDateTime.now());
        binMapper.updateById(bin);
        evidenceService.record(FishingChangeEvidence.ENTITY_BIN, bin.getId(),
                FishingChangeEvidence.ACTION_BIN_INSPECT, request.getRequestId(), request.getOperator(),
                before, bin, "库位" + bin.getBinNo() + "检验完成，待检标识与状态同步复位");
        return bin;
    }

    public FishingBin getOrThrow(Long id) {
        FishingBin bin = binMapper.selectById(id);
        if (bin == null) {
            throw new IllegalArgumentException("库位不存在，id=" + id);
        }
        return bin;
    }

    public List<FishingBin> list(String specCode, String status) {
        QueryWrapper<FishingBin> wrapper = new QueryWrapper<>();
        wrapper.eq(specCode != null && !specCode.trim().isEmpty(), "spec_code", specCode);
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return binMapper.selectList(wrapper);
    }

    private FishingBin copyOf(FishingBin bin) {
        FishingBin copy = new FishingBin();
        org.springframework.beans.BeanUtils.copyProperties(bin, copy);
        return copy;
    }
}
