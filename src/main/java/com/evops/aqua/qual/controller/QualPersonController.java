package com.evops.aqua.qual.controller;

import com.evops.aqua.qual.dto.BatchFreezeRequest;
import com.evops.aqua.qual.dto.PersonRegisterRequest;
import com.evops.aqua.qual.dto.RoleChangeRequest;
import com.evops.aqua.qual.entity.QualPerson;
import com.evops.aqua.qual.entity.RoleChange;
import com.evops.aqua.qual.service.CertificateService;
import com.evops.aqua.qual.service.FreezeService;
import com.evops.aqua.qual.service.PersonService;
import com.evops.aqua.qual.service.QualAuthService;
import com.evops.aqua.qual.service.RoleScopeService;
import com.evops.aqua.qual.service.TempAuthService;
import com.evops.common.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 养殖人员档案：登记 / 列表 / 详情 / 导出 / 岗位变更 / 批量冻结 / 解冻 / 有效权限。
 * 列表、详情、CSV 导出共用同一 loadRows 行源，口径一致可交叉核对。
 */
@RestController
@RequestMapping("/api/aqua/qual/persons")
public class QualPersonController {

    private final PersonService personService;
    private final CertificateService certificateService;
    private final TempAuthService tempAuthService;
    private final RoleScopeService roleScopeService;
    private final QualAuthService qualAuthService;
    private final FreezeService freezeService;

    public QualPersonController(PersonService personService, CertificateService certificateService,
                                TempAuthService tempAuthService, RoleScopeService roleScopeService,
                                QualAuthService qualAuthService, FreezeService freezeService) {
        this.personService = personService;
        this.certificateService = certificateService;
        this.tempAuthService = tempAuthService;
        this.roleScopeService = roleScopeService;
        this.qualAuthService = qualAuthService;
        this.freezeService = freezeService;
    }

    @PostMapping
    public ApiResponse<QualPerson> register(@Validated @RequestBody PersonRegisterRequest request) {
        return ApiResponse.ok(personService.register(request));
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String role,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(personService.loadRows(role, status, keyword));
    }

    /** CSV 导出：与列表同一 loadRows 行源、同一过滤口径。 */
    @GetMapping(value = "/export", produces = "text/csv; charset=UTF-8")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String role,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String keyword) {
        String csv = personService.exportCsv(role, status, keyword);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"qual-persons.csv\"");
        return ResponseEntity.ok().headers(headers).body(csv.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        QualPerson person = personService.getOrThrow(id);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("person", person);
        detail.put("roleScope", roleScopeService.getByRole(person.getRoleCode()));
        detail.put("certificates", certificateService.listByPerson(id));
        detail.put("tempAuths", tempAuthService.listByPerson(id));
        detail.put("roleChanges", personService.listRoleChanges(id));
        detail.put("permissions", qualAuthService.effectivePermissions(person));
        return ApiResponse.ok(detail);
    }

    @GetMapping("/{id}/permissions")
    public ApiResponse<Map<String, Object>> permissions(@PathVariable Long id) {
        return ApiResponse.ok(qualAuthService.effectivePermissions(personService.getOrThrow(id)));
    }

    @PostMapping("/{id}/role-change")
    public ApiResponse<RoleChange> changeRole(@PathVariable Long id,
                                              @Validated @RequestBody RoleChangeRequest request) {
        return ApiResponse.ok(personService.changeRole(id, request));
    }

    @PostMapping("/{id}/unfreeze")
    public ApiResponse<QualPerson> unfreeze(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(personService.unfreeze(id, body == null ? null : body.get("operator")));
    }

    /** 批量冻结：逐人返回冻结结果与受影响工单/未完成任务清单；requestId 幂等。 */
    @PostMapping("/batch-freeze")
    public ApiResponse<Map<String, Object>> batchFreeze(
            @Validated @RequestBody BatchFreezeRequest request) {
        return ApiResponse.ok(freezeService.freezeBatch(request));
    }
}
