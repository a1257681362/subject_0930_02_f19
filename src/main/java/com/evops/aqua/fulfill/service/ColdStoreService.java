package com.evops.aqua.fulfill.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.fulfill.dto.ColdStoreRegisterRequest;
import com.evops.aqua.fulfill.entity.ColdStore;
import com.evops.aqua.fulfill.entity.FulfillChangeEvidence;
import com.evops.aqua.fulfill.mapper.ColdStoreMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 冷藏库主数据：容量与在库重量账；requestId 幂等。
 */
@Service
public class ColdStoreService {

    private final ColdStoreMapper storeMapper;
    private final FulfillEvidenceService evidenceService;

    public ColdStoreService(ColdStoreMapper storeMapper, FulfillEvidenceService evidenceService) {
        this.storeMapper = storeMapper;
        this.evidenceService = evidenceService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ColdStore register(ColdStoreRegisterRequest request) {
        ColdStore duplicated = storeMapper.selectOne(new QueryWrapper<ColdStore>()
                .eq("request_id", request.getRequestId()).last("limit 1"));
        if (duplicated != null) {
            return duplicated;
        }
        ColdStore sameNo = storeMapper.selectOne(new QueryWrapper<ColdStore>()
                .eq("store_no", request.getStoreNo()).last("limit 1"));
        if (sameNo != null) {
            throw new IllegalArgumentException("冷藏库编号已存在：" + request.getStoreNo());
        }
        if (request.getCapacityKg() == null || request.getCapacityKg().signum() <= 0) {
            throw new IllegalArgumentException("冷藏库容量必须大于0");
        }
        ColdStore store = new ColdStore();
        store.setStoreNo(request.getStoreNo());
        store.setStoreName(request.getStoreName());
        store.setCapacityKg(request.getCapacityKg());
        store.setStoredKg(BigDecimal.ZERO);
        store.setStatus(ColdStore.STATUS_ENABLED);
        store.setOperator(request.getOperator());
        store.setRequestId(request.getRequestId());
        store.setRemark(request.getRemark());
        storeMapper.insert(store);
        evidenceService.record(FulfillChangeEvidence.ENTITY_COLD_STORE, store.getId(),
                FulfillChangeEvidence.ACTION_STORE_REGISTER, request.getRequestId(), request.getOperator(),
                null, store, "登记冷藏库" + store.getStoreNo() + "（" + store.getStoreName()
                        + "），容量" + store.getCapacityKg() + "kg");
        return store;
    }

    public ColdStore enabledStoreOrThrow(Long storeId) {
        ColdStore store = storeMapper.selectById(storeId);
        if (store == null) {
            throw new IllegalArgumentException("冷藏库不存在，id=" + storeId);
        }
        if (!ColdStore.STATUS_ENABLED.equals(store.getStatus())) {
            throw new IllegalArgumentException("冷藏库" + store.getStoreNo() + "已停用，不能入库");
        }
        return store;
    }

    /**
     * 容量校验：在库 + 本次净重不得超过容量（冻结箱重量始终占用容量）。
     */
    public void requireCapacity(ColdStore store, BigDecimal addNetKg) {
        if (store.getStoredKg().add(addNetKg).compareTo(store.getCapacityKg()) > 0) {
            throw new IllegalArgumentException("冷藏库" + store.getStoreNo() + "容量不足：在库"
                    + store.getStoredKg() + "kg + 本次" + addNetKg + "kg 超过容量"
                    + store.getCapacityKg() + "kg");
        }
    }

    public List<ColdStore> list(String status) {
        QueryWrapper<ColdStore> wrapper = new QueryWrapper<>();
        wrapper.eq(status != null && !status.trim().isEmpty(), "status", status);
        wrapper.orderByAsc("id");
        return storeMapper.selectList(wrapper);
    }
}
