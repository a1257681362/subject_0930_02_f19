package com.evops.aqua.feed.controller;

import com.evops.aqua.feed.dto.FeedBatchStockRequest;
import com.evops.aqua.feed.entity.FeedBatch;
import com.evops.aqua.feed.service.FeedBatchService;
import com.evops.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 饲料批号：入库登记、库存查询。批号推进受海水养殖管理权限开关控制。
 */
@RestController
@RequestMapping("/api/aqua/feed/batches")
public class FeedBatchController {

    private final FeedBatchService batchService;

    public FeedBatchController(FeedBatchService batchService) {
        this.batchService = batchService;
    }

    @PostMapping
    public ApiResponse<FeedBatch> stock(@Validated @RequestBody FeedBatchStockRequest request) {
        return ApiResponse.ok(batchService.stock(request));
    }

    @GetMapping
    public ApiResponse<List<FeedBatch>> list(@RequestParam(required = false) String feedName) {
        return ApiResponse.ok(batchService.list(feedName));
    }
}
