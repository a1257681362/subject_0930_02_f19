package com.evops.aqua.maint.controller;

import com.evops.aqua.maint.dto.WorkerRegisterRequest;
import com.evops.aqua.maint.entity.MaintWorker;
import com.evops.aqua.maint.service.WorkerService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 维修人员档案：派工对象与资质。
 */
@RestController
@RequestMapping("/api/aqua/maint/workers")
public class WorkerController {

    private final WorkerService workerService;

    public WorkerController(WorkerService workerService) {
        this.workerService = workerService;
    }

    @PostMapping
    public ApiResponse<MaintWorker> register(@Validated @RequestBody WorkerRegisterRequest request) {
        return ApiResponse.ok(workerService.register(request));
    }

    @GetMapping
    public ApiResponse<List<MaintWorker>> list() {
        return ApiResponse.ok(workerService.listAll());
    }
}
