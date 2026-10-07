package com.evops.aqua.tide.controller;

import com.evops.aqua.tide.dto.CloseWindowRequest;
import com.evops.aqua.tide.dto.WindowGenerateRequest;
import com.evops.aqua.tide.entity.TideWindow;
import com.evops.aqua.tide.service.TideWindowService;
import com.evops.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 换水窗口：按海区潮汐表生成候选 → 叠加禁作天气与人员班次 → 可执行窗口。
 * 归属日统一口径：窗口开始时间所在自然日（跨午夜归开始日）。
 */
@RestController
@RequestMapping("/api/aqua/tide/windows")
public class TideWindowController {

    private final TideWindowService windowService;

    public TideWindowController(TideWindowService windowService) {
        this.windowService = windowService;
    }

    /** 生成可执行窗口：返回成窗清单与被排除候选及各自原因；requestId 幂等。 */
    @PostMapping("/generate")
    public ApiResponse<Map<String, Object>> generate(@Validated @RequestBody WindowGenerateRequest request) {
        return ApiResponse.ok(windowService.generate(request));
    }

    /** 窗口查询：按海区与归属日（同一口径：归属日 = 窗口开始时间所在自然日）。 */
    @GetMapping
    public ApiResponse<List<TideWindow>> list(@RequestParam(required = false) String seaArea,
                                              @RequestParam(required = false)
                                              @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                              @RequestParam(required = false) String status) {
        return ApiResponse.ok(windowService.list(seaArea, date, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<TideWindow> detail(@PathVariable Long id) {
        return ApiResponse.ok(windowService.getOrThrow(id));
    }

    /** 临时关闭窗口：向受影响班组发通知并等待签收回执；重复关闭幂等不多发。 */
    @PostMapping("/{id}/close")
    public ApiResponse<Map<String, Object>> close(@PathVariable Long id,
                                                  @Validated @RequestBody CloseWindowRequest request) {
        return ApiResponse.ok(windowService.close(id, request));
    }
}
