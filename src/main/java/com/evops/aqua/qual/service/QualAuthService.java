package com.evops.aqua.qual.service;

import com.evops.aqua.qual.entity.QualPerson;
import com.evops.aqua.qual.entity.RoleScope;
import com.evops.aqua.qual.entity.TempAuth;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 岗位授权判定：有效权限 = 岗位范围（需持有效岗位证书）+ 生效中的临时授权，
 * 冻结/离职人员全部收回。所有判定按当前时间实时计算——
 * 证书过期、临时授权到点的当刻权限即收回，不依赖定时扫描。
 */
@Service
public class QualAuthService {

    private final PersonService personService;
    private final RoleScopeService roleScopeService;
    private final CertificateService certificateService;
    private final TempAuthService tempAuthService;

    public QualAuthService(PersonService personService, RoleScopeService roleScopeService,
                           CertificateService certificateService, TempAuthService tempAuthService) {
        this.personService = personService;
        this.roleScopeService = roleScopeService;
        this.certificateService = certificateService;
        this.tempAuthService = tempAuthService;
    }

    /**
     * 有效权限视图：menus/apis 为当前实际可用的菜单与接口权限码；
     * blocks 逐条说明被收回的原因（冻结、证书缺失/过期、临时授权到期）。
     */
    public Map<String, Object> effectivePermissions(QualPerson person) {
        List<String> menus = new ArrayList<>();
        List<String> apis = new ArrayList<>();
        List<Map<String, Object>> tempGrants = new ArrayList<>();
        List<String> blocks = new ArrayList<>();

        if (!QualPerson.STATUS_ACTIVE.equals(person.getStatus())) {
            blocks.add("人员状态 " + person.getStatus() + "，全部菜单与接口权限已收回");
        } else {
            RoleScope scope = roleScopeService.getByRole(person.getRoleCode());
            if (scope == null) {
                blocks.add("岗位 " + person.getRoleCode() + " 未配置可操作范围");
            } else if (!certificateService.hasValidRequiredCert(
                    person.getId(), scope.getRequiredCertType())) {
                blocks.add("岗位所需证书[" + scope.getRequiredCertType()
                        + "]无效或已过期，岗位菜单与接口权限已收回");
            } else {
                menus.addAll(RoleScopeService.splitCodes(scope.getMenuCodes()));
                apis.addAll(RoleScopeService.splitCodes(scope.getApiPerms()));
            }
            LocalDateTime now = LocalDateTime.now();
            for (TempAuth auth : tempAuthService.listByPerson(person.getId())) {
                if (!TempAuth.STATUS_ACTIVE.equals(auth.getStatus())) {
                    continue;
                }
                if (!now.isBefore(auth.getEffectiveTo())) {
                    blocks.add("临时授权（审批单 " + auth.getApprovalTicket() + "）已于 "
                            + auth.getEffectiveTo() + " 到期，权限自动收回");
                    continue;
                }
                if (now.isBefore(auth.getEffectiveFrom())) {
                    continue;
                }
                Map<String, Object> grant = new LinkedHashMap<>();
                grant.put("permCodes", RoleScopeService.splitCodes(auth.getPermCodes()));
                grant.put("approvalTicket", auth.getApprovalTicket());
                grant.put("effectiveTo", auth.getEffectiveTo());
                tempGrants.add(grant);
                for (String code : RoleScopeService.splitCodes(auth.getPermCodes())) {
                    if (code.startsWith("menu:") && !menus.contains(code)) {
                        menus.add(code);
                    }
                    if (code.startsWith("api:") && !apis.contains(code)) {
                        apis.add(code);
                    }
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personId", person.getId());
        result.put("personCode", person.getPersonCode());
        result.put("personName", person.getPersonName());
        result.put("roleCode", person.getRoleCode());
        result.put("status", person.getStatus());
        result.put("menus", menus);
        result.put("apis", apis);
        result.put("tempAuths", tempGrants);
        result.put("blocks", blocks);
        return result;
    }

    /** 权限核验：menu: 精确匹配；api: 精确或 /** 前缀匹配。返回 allowed 与原因。 */
    public Map<String, Object> check(String personCode, String permission) {
        QualPerson person = personService.getByCodeOrThrow(personCode);
        if (permission == null || permission.trim().isEmpty()) {
            throw new IllegalArgumentException("待核验权限码不能为空");
        }
        String perm = permission.trim();
        Map<String, Object> effective = effectivePermissions(person);
        @SuppressWarnings("unchecked")
        List<String> menus = (List<String>) effective.get("menus");
        @SuppressWarnings("unchecked")
        List<String> apis = (List<String>) effective.get("apis");
        boolean allowed = matches(perm, menus, apis);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("personCode", person.getPersonCode());
        result.put("permission", perm);
        result.put("allowed", allowed);
        result.put("reason", allowed
                ? "权限有效"
                : "权限未授予或已收回" + formatBlocks(effective.get("blocks")));
        return result;
    }

    static boolean matches(String perm, List<String> menus, List<String> apis) {
        if (perm.startsWith("menu:")) {
            return menus.contains(perm);
        }
        if (perm.startsWith("api:")) {
            for (String granted : apis) {
                if (granted.equals(perm)) {
                    return true;
                }
                if (granted.endsWith("/**")
                        && perm.startsWith(granted.substring(0, granted.length() - 3))) {
                    return true;
                }
            }
        }
        return false;
    }

    private String formatBlocks(Object blocks) {
        if (!(blocks instanceof List) || ((List<?>) blocks).isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("：");
        for (Object block : (List<?>) blocks) {
            sb.append(block).append("；");
        }
        return sb.toString();
    }
}
