package com.evops.aqua.report.controller;

import com.evops.aqua.report.entity.ReportJob;
import com.evops.aqua.report.entity.ReportJobAttempt;
import com.evops.aqua.report.service.ReportJobService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 月报生成任务：手工触发 Quartz 同逻辑扫描、查询失败任务、重试。
 * 失败任务保留 FAILED 终态与完整异常链路（attempts），重试只追加链路，成功后也不清除历史失败。
 */
@RestController
@RequestMapping("/api/aqua/report/jobs")
public class ReportJobController {

    private final ReportJobService jobService;

    public ReportJobController(ReportJobService jobService) {
        this.jobService = jobService;
    }

    /** 手工触发与 Quartz 同一扫描逻辑（验收/补跑用）：为全部海区生成指定月或上月月报。 */
    @PostMapping("/scan")
    public ApiResponse<Map<String, Object>> scan(@RequestParam(required = false) String reportMonth) {
        String month = reportMonth == null || reportMonth.trim().isEmpty()
                ? java.time.YearMonth.now().minusMonths(1).toString() : reportMonth;
        return ApiResponse.ok(jobService.scheduledScan(month));
    }

    @GetMapping
    public ApiResponse<List<ReportJob>> list(@RequestParam(required = false) String status) {
        return ApiResponse.ok(jobService.listJobs(status));
    }

    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
        ReportJob job = jobService.getJob(id);
        List<ReportJobAttempt> attempts = jobService.listAttempts(id);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("job", job);
        view.put("attempts", attempts);
        return ApiResponse.ok(view);
    }

    /** 重试失败任务：attempts+1，异常链路逐次保留。 */
    @PostMapping("/{id}/retry")
    public ApiResponse<ReportJob> retry(@PathVariable Long id) {
        return ApiResponse.ok(jobService.retry(id));
    }
}
