package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.entity.DiseaseIsolation;
import com.evops.aqua.disease.entity.PondGuard;
import com.evops.aqua.disease.mapper.DiseaseIsolationMapper;
import com.evops.aqua.disease.mapper.PondGuardMapper;
import org.springframework.stereotype.Component;

/**
 * 隔离守卫：供台账（投苗/换池/出池）调用的只读校验。
 * 只依赖本模块 Mapper，不反向依赖台账 Service，避免循环依赖。
 * 隔离中的批次禁止换池（混养）与出池；隔离池在隔离期间禁止投苗混养。
 */
@Component
public class IsolationGuard {

    private final DiseaseIsolationMapper isolationMapper;
    private final PondGuardMapper pondGuardMapper;

    public IsolationGuard(DiseaseIsolationMapper isolationMapper, PondGuardMapper pondGuardMapper) {
        this.isolationMapper = isolationMapper;
        this.pondGuardMapper = pondGuardMapper;
    }

    /** 批次处于隔离中则拒绝：禁止换池（混养）与出池。 */
    public void requireBatchNotIsolated(Long batchId, String action) {
        DiseaseIsolation active = activeIsolationOfBatch(batchId);
        if (active != null) {
            throw new IllegalArgumentException("批次正处于隔离中（隔离单" + active.getIsolationNo()
                    + "，隔离池id=" + active.getToPondId() + "），禁止" + action
                    + "；须由兽医复核解除隔离后再操作");
        }
    }

    /** 池塘处于隔离占用或为隔离池时拒绝普通投苗：禁止混养。 */
    public void requirePondOpenForStocking(Long pondId) {
        DiseaseIsolation active = activeIsolationInPond(pondId);
        if (active != null) {
            throw new IllegalArgumentException("池塘id=" + pondId + "存在在隔离批次（隔离单"
                    + active.getIsolationNo() + "），隔离期间禁止投苗混养");
        }
        PondGuard guard = pondGuardMapper.selectOne(
                new QueryWrapper<PondGuard>().eq("pond_id", pondId));
        if (guard != null && PondGuard.ROLE_ISOLATION.equals(guard.getPondRole())) {
            throw new IllegalArgumentException("池塘id=" + pondId
                    + "已登记为隔离池，禁止普通投苗混养；如需调整请先变更隔离调度档案");
        }
    }

    public DiseaseIsolation activeIsolationOfBatch(Long batchId) {
        return isolationMapper.selectOne(new QueryWrapper<DiseaseIsolation>()
                .eq("batch_id", batchId)
                .eq("status", DiseaseIsolation.STATUS_ISOLATED)
                .last("limit 1"));
    }

    public DiseaseIsolation activeIsolationInPond(Long pondId) {
        return isolationMapper.selectOne(new QueryWrapper<DiseaseIsolation>()
                .eq("to_pond_id", pondId)
                .eq("status", DiseaseIsolation.STATUS_ISOLATED)
                .last("limit 1"));
    }
}
