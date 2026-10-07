package com.evops.aqua.release.controller;

import com.evops.aqua.release.dto.ReleaseSignerRequest;
import com.evops.aqua.release.entity.ReleaseSigner;
import com.evops.aqua.release.service.ReleaseSignerService;
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

/**
 * 授权签字人：登记/授权停用。电子签名在检验单接口上凭工号+口令完成。
 */
@RestController
@RequestMapping("/api/aqua/release/signers")
public class ReleaseSignerController {

    private final ReleaseSignerService signerService;

    public ReleaseSignerController(ReleaseSignerService signerService) {
        this.signerService = signerService;
    }

    @PostMapping
    public ApiResponse<ReleaseSigner> register(@Validated @RequestBody ReleaseSignerRequest request) {
        return ApiResponse.ok(signerService.mask(signerService.register(request)));
    }

    @GetMapping
    public ApiResponse<List<ReleaseSigner>> list() {
        List<ReleaseSigner> signers = signerService.list();
        java.util.List<ReleaseSigner> masked = new java.util.ArrayList<>();
        for (ReleaseSigner s : signers) {
            masked.add(signerService.mask(s));
        }
        return ApiResponse.ok(masked);
    }

    /** 授权 / 停止授权 */
    @PostMapping("/{employeeNo}/authorization")
    public ApiResponse<ReleaseSigner> setAuthorized(@PathVariable String employeeNo,
                                                    @RequestParam boolean authorized,
                                                    @RequestParam(required = false) String reason) {
        return ApiResponse.ok(signerService.mask(
                signerService.setAuthorized(employeeNo, authorized, reason)));
    }
}
