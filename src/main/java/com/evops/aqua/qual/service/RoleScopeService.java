package com.evops.aqua.qual.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.aqua.qual.dto.RoleScopeUpdateRequest;
import com.evops.aqua.qual.entity.RoleScope;
import com.evops.aqua.qual.mapper.RoleScopeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 岗位可操作范围：兽医/采样员/捕捞员/设备员各自配置菜单与接口权限码。
 * 范围调整只改配置行并留证；权限是否真正生效由 QualAuthService 按证书/冻结/临时授权实时计算。
 */
@Service
public class RoleScopeService {

    private final RoleScopeMapper roleScopeMapper;
    private final QualEvidenceService evidenceService;

    public RoleScopeService(RoleScopeMapper roleScopeMapper, QualEvidenceService evidenceService) {
        this.roleScopeMapper = roleScopeMapper;
        this.evidenceService = evidenceService;
    }

    public List<RoleScope> listAll() {
        return roleScopeMapper.selectList(new QueryWrapper<RoleScope>().orderByAsc("id"));
    }

    public RoleScope getByRole(String roleCode) {
        if (roleCode == null) {
            return null;
        }
        return roleScopeMapper.selectOne(
                new QueryWrapper<RoleScope>().eq("role_code", roleCode.trim().toUpperCase()));
    }

    /** 岗位必须已配置范围，否则拒绝登记/变更。 */
    public RoleScope requireRole(String roleCode) {
        RoleScope scope = getByRole(roleCode);
        if (scope == null) {
            throw new IllegalArgumentException("未知岗位：" + roleCode + "（支持 VET/SAMPLER/FISHER/EQUIPMENT_OPERATOR）");
        }
        return scope;
    }

    @Transactional(rollbackFor = Exception.class)
    public RoleScope updateScope(String roleCode, RoleScopeUpdateRequest request) {
        RoleScope scope = requireRole(roleCode);
        String before = snapshot(scope);
        scope.setMenuCodes(normalizeCodes(request.getMenuCodes(), "menu:"));
        scope.setApiPerms(normalizeCodes(request.getApiPerms(), "api:"));
        scope.setRequiredCertType(request.getRequiredCertType() == null
                ? null : request.getRequiredCertType().trim().toUpperCase());
        scope.setUpdatedBy(request.getOperator());
        scope.setRemark(request.getRemark());
        roleScopeMapper.updateById(scope);
        evidenceService.record("ROLE_SCOPE", scope.getId(), "ROLE_SCOPE_UPDATE", null,
                request.getOperator(), before, snapshot(scope),
                "岗位[" + scope.getRoleCode() + "]可操作范围调整");
        return scope;
    }

    static List<String> splitCodes(String codes) {
        List<String> result = new ArrayList<>();
        if (codes == null || codes.trim().isEmpty()) {
            return result;
        }
        for (String code : codes.split(",")) {
            String c = code.trim();
            if (!c.isEmpty() && !result.contains(c)) {
                result.add(c);
            }
        }
        return result;
    }

    /** 规范化权限码：去重去空，校验前缀（menu: 或 api:）。 */
    static String normalizeCodes(String codes, String requiredPrefix) {
        List<String> normalized = splitCodes(codes);
        for (String code : normalized) {
            if (!code.startsWith(requiredPrefix)) {
                throw new IllegalArgumentException("权限码必须以 " + requiredPrefix + " 开头：" + code);
            }
        }
        return String.join(",", normalized);
    }

    private String snapshot(RoleScope scope) {
        return "role=" + scope.getRoleCode()
                + ", menus=" + scope.getMenuCodes()
                + ", apis=" + scope.getApiPerms()
                + ", requiredCert=" + scope.getRequiredCertType();
    }
}
