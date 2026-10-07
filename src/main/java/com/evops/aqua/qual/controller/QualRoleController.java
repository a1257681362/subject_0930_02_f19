package com.evops.aqua.qual.controller;

import com.evops.aqua.qual.dto.RoleScopeUpdateRequest;
import com.evops.aqua.qual.entity.RoleScope;
import com.evops.aqua.qual.service.RoleScopeService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 岗位可操作范围：兽医/采样员/捕捞员/设备员各自行配置菜单与接口权限码。
 */
@RestController
@RequestMapping("/api/aqua/qual/roles")
public class QualRoleController {

    private final RoleScopeService roleScopeService;

    public QualRoleController(RoleScopeService roleScopeService) {
        this.roleScopeService = roleScopeService;
    }

    @GetMapping
    public ApiResponse<List<RoleScope>> list() {
        return ApiResponse.ok(roleScopeService.listAll());
    }

    @PutMapping("/{role}/scope")
    public ApiResponse<RoleScope> updateScope(@PathVariable String role,
                                              @Validated @RequestBody RoleScopeUpdateRequest request) {
        return ApiResponse.ok(roleScopeService.updateScope(role, request));
    }
}
