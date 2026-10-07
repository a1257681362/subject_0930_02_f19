package com.evops.aqua.qual.controller;

import com.evops.aqua.qual.dto.CertificateRegisterRequest;
import com.evops.aqua.qual.entity.QualCertificate;
import com.evops.aqua.qual.service.CertificateService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 资质证书：登记幂等、吊销留证；过期由扫描置位 + 权限计算实时收回。
 */
@RestController
@RequestMapping("/api/aqua/qual/certificates")
public class QualCertificateController {

    private final CertificateService certificateService;

    public QualCertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping
    public ApiResponse<QualCertificate> register(
            @Validated @RequestBody CertificateRegisterRequest request) {
        return ApiResponse.ok(certificateService.register(request));
    }

    @GetMapping
    public ApiResponse<List<QualCertificate>> list(@RequestParam Long personId) {
        return ApiResponse.ok(certificateService.listByPerson(personId));
    }

    @PostMapping("/{id}/revoke")
    public ApiResponse<QualCertificate> revoke(@PathVariable Long id,
                                               @RequestBody(required = false) Map<String, String> body) {
        return ApiResponse.ok(certificateService.revoke(id,
                body == null ? null : body.get("operator"),
                body == null ? null : body.get("reason")));
    }
}
