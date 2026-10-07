package com.evops.aqua.med.controller;

import com.evops.aqua.med.dto.MedDrugRequest;
import com.evops.aqua.med.entity.MedDrug;
import com.evops.aqua.med.service.MedDrugService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 药品档案入口。
 */
@RestController
@RequestMapping("/api/aqua/med/drugs")
public class MedDrugController {

    private final MedDrugService drugService;

    public MedDrugController(MedDrugService drugService) {
        this.drugService = drugService;
    }

    @PostMapping
    public ApiResponse<MedDrug> register(@Validated @RequestBody MedDrugRequest request) {
        return ApiResponse.ok(drugService.register(request));
    }

    @GetMapping
    public ApiResponse<List<MedDrug>> list() {
        return ApiResponse.ok(drugService.list());
    }
}
