package com.evops.aqua.controller;

import com.evops.aqua.dto.PondCreateRequest;
import com.evops.aqua.dto.PondDisableRequest;
import com.evops.aqua.entity.Pond;
import com.evops.aqua.service.PondService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 养殖池档案入口。
 */
@RestController
@RequestMapping("/api/aqua/ponds")
public class PondController {

    private final PondService pondService;

    public PondController(PondService pondService) {
        this.pondService = pondService;
    }

    @PostMapping
    public ApiResponse<Pond> create(@Validated @RequestBody PondCreateRequest request) {
        return ApiResponse.ok(pondService.create(request));
    }

    @GetMapping
    public ApiResponse<List<Pond>> list() {
        return ApiResponse.ok(pondService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<Pond> detail(@PathVariable Long id) {
        return ApiResponse.ok(pondService.getOrThrow(id));
    }

    /**
     * 将海水养殖管理对象置为无效：历史数据保留不删，
     * 成本分摊页面/导出/后台报表随后同口径不再统计该池。
     */
    @PostMapping("/{id}/disable")
    public ApiResponse<Pond> disable(@PathVariable Long id,
                                     @Validated @RequestBody PondDisableRequest request) {
        return ApiResponse.ok(pondService.disable(id, request));
    }
}
