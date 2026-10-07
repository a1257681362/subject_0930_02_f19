package com.evops.aqua.release.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.release.dto.ReleaseSignerRequest;
import com.evops.aqua.release.entity.ReleaseChangeEvidence;
import com.evops.aqua.release.entity.ReleaseSigner;
import com.evops.aqua.release.mapper.ReleaseSignerMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 授权签字人：登记/停用；电子签名前核验授权状态与签名口令。
 */
@Service
public class ReleaseSignerService {

    private final ReleaseSignerMapper signerMapper;
    private final ReleaseEvidenceService evidenceService;

    public ReleaseSignerService(ReleaseSignerMapper signerMapper,
                                ReleaseEvidenceService evidenceService) {
        this.signerMapper = signerMapper;
        this.evidenceService = evidenceService;
    }

    /** 工号唯一：重复登记拒绝。 */
    @Transactional(rollbackFor = Exception.class)
    public ReleaseSigner register(ReleaseSignerRequest request) {
        Long exists = signerMapper.selectCount(new QueryWrapper<ReleaseSigner>()
                .eq("employee_no", request.getEmployeeNo()));
        if (exists != null && exists > 0) {
            throw new IllegalArgumentException("签字人工号已存在：" + request.getEmployeeNo());
        }
        ReleaseSigner signer = new ReleaseSigner();
        signer.setEmployeeNo(request.getEmployeeNo().trim());
        signer.setSignerName(request.getSignerName().trim());
        signer.setSignSecret(request.getSignSecret());
        signer.setAuthorizedFlag(request.getAuthorizedFlag() == null
                ? Boolean.TRUE : request.getAuthorizedFlag());
        signer.setRemark(request.getRemark());
        signerMapper.insert(signer);
        evidenceService.record(ReleaseChangeEvidence.ENTITY_SIGNER, signer.getId(),
                ReleaseChangeEvidence.ACTION_CREATE, request.getRequestId(),
                ReleaseEvidenceService.currentOperator(), null, mask(signer),
                "登记授权签字人" + signer.getSignerName());
        return signer;
    }

    /** 授权/停用切换：停用后不能再电子签名。 */
    @Transactional(rollbackFor = Exception.class)
    public ReleaseSigner setAuthorized(String employeeNo, boolean authorized, String reason) {
        ReleaseSigner before = getByEmployeeNo(employeeNo);
        ReleaseSigner after = copyOf(before);
        after.setAuthorizedFlag(authorized);
        after.setRemark(reason == null || reason.trim().isEmpty() ? before.getRemark() : reason);
        signerMapper.updateById(after);
        evidenceService.record(ReleaseChangeEvidence.ENTITY_SIGNER, before.getId(),
                ReleaseChangeEvidence.ACTION_UPDATE, null,
                ReleaseEvidenceService.currentOperator(), mask(before), mask(after),
                "签字人" + employeeNo + (authorized ? "恢复授权" : "停止授权"));
        return after;
    }

    /** 电子签名核验：签字人存在、处于授权状态、口令正确，否则拒绝。返回签字人（用于取姓名）。 */
    public ReleaseSigner authenticate(String employeeNo, String signSecret) {
        List<ReleaseSigner> rows = signerMapper.selectList(new QueryWrapper<ReleaseSigner>()
                .eq("employee_no", employeeNo));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("签字人工号不存在，无电子签名权限：" + employeeNo);
        }
        ReleaseSigner signer = rows.get(0);
        if (!Boolean.TRUE.equals(signer.getAuthorizedFlag())) {
            throw new IllegalArgumentException("签字人" + employeeNo + "已停止授权，不能电子签名放行");
        }
        if (!signer.getSignSecret().equals(signSecret)) {
            throw new IllegalArgumentException("签名口令核验失败，拒绝电子签名");
        }
        return signer;
    }

    public ReleaseSigner getByEmployeeNo(String employeeNo) {
        List<ReleaseSigner> rows = signerMapper.selectList(new QueryWrapper<ReleaseSigner>()
                .eq("employee_no", employeeNo));
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("签字人工号不存在：" + employeeNo);
        }
        return rows.get(0);
    }

    public List<ReleaseSigner> list() {
        return signerMapper.selectList(new QueryWrapper<ReleaseSigner>().orderByAsc("id"));
    }

    /** 证据/对外输出不带签名口令。 */
    public ReleaseSigner mask(ReleaseSigner s) {
        ReleaseSigner m = new ReleaseSigner();
        m.setId(s.getId());
        m.setEmployeeNo(s.getEmployeeNo());
        m.setSignerName(s.getSignerName());
        m.setSignSecret("******");
        m.setAuthorizedFlag(s.getAuthorizedFlag());
        m.setRemark(s.getRemark());
        return m;
    }

    private ReleaseSigner copyOf(ReleaseSigner s) {
        ReleaseSigner c = new ReleaseSigner();
        c.setId(s.getId());
        c.setEmployeeNo(s.getEmployeeNo());
        c.setSignerName(s.getSignerName());
        c.setSignSecret(s.getSignSecret());
        c.setAuthorizedFlag(s.getAuthorizedFlag());
        c.setRemark(s.getRemark());
        return c;
    }
}
