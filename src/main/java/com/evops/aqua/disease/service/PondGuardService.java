package com.evops.aqua.disease.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.disease.dto.PondGuardUpsertRequest;
import com.evops.aqua.disease.entity.DiseaseChangeEvidence;
import com.evops.aqua.disease.entity.PondGuard;
import com.evops.aqua.disease.mapper.PondGuardMapper;
import com.evops.aqua.service.PondService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 池塘隔离调度档案：按池一行，登记隔离池角色、容量、消毒有效期、调入权限。
 * 推荐隔离池与调度前校验均以本档案为准；每次变更留证并记录操作者。
 */
@Service
public class PondGuardService {

    private final PondGuardMapper guardMapper;
    private final PondService pondService;
    private final DiseaseEvidenceService evidenceService;

    public PondGuardService(PondGuardMapper guardMapper,
                            PondService pondService,
                            DiseaseEvidenceService evidenceService) {
        this.guardMapper = guardMapper;
        this.pondService = pondService;
        this.evidenceService = evidenceService;
    }

    /**
     * 登记或更新池塘隔离调度档案（按 pondId 幂等覆盖）。
     */
    @Transactional(rollbackFor = Exception.class)
    public PondGuard upsert(PondGuardUpsertRequest request) {
        pondService.getOrThrow(request.getPondId());
        String role = PondGuard.ROLE_ISOLATION.equals(request.getPondRole())
                ? PondGuard.ROLE_ISOLATION : PondGuard.ROLE_NORMAL;
        if (PondGuard.ROLE_ISOLATION.equals(role) && request.getCapacity() == null) {
            throw new IllegalArgumentException("隔离池必须登记容量（尾数），否则无法做调度前容量校验");
        }
        PondGuard before = guardMapper.selectOne(
                new QueryWrapper<PondGuard>().eq("pond_id", request.getPondId()));
        PondGuard guard = before == null ? new PondGuard() : before;
        guard.setPondId(request.getPondId());
        guard.setPondRole(role);
        guard.setCapacity(request.getCapacity());
        guard.setDisinfectedUntil(request.getDisinfectedUntil());
        guard.setTransferInAllowed(request.getTransferInAllowed() == null
                ? Boolean.TRUE : request.getTransferInAllowed());
        guard.setOperator(request.getOperator());
        guard.setRemark(request.getRemark());
        if (before == null) {
            guardMapper.insert(guard);
        } else {
            guardMapper.updateById(guard);
        }
        evidenceService.record(DiseaseChangeEvidence.ENTITY_POND_GUARD, guard.getId(),
                DiseaseChangeEvidence.ACTION_UPSERT_GUARD, request.getRequestId(), request.getOperator(),
                before, guard,
                (before == null ? "登记" : "更新") + "池塘id=" + request.getPondId() + "隔离调度档案：角色"
                        + role + "，容量" + request.getCapacity() + "，消毒有效期至"
                        + request.getDisinfectedUntil() + "，允许调入=" + guard.getTransferInAllowed());
        return guard;
    }

    public PondGuard getByPond(Long pondId) {
        return guardMapper.selectOne(new QueryWrapper<PondGuard>().eq("pond_id", pondId));
    }

    public List<PondGuard> list() {
        return guardMapper.selectList(new QueryWrapper<PondGuard>().orderByAsc("id"));
    }
}
