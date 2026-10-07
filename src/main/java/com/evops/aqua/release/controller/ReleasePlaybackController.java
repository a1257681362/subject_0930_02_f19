package com.evops.aqua.release.controller;

import com.evops.aqua.release.dto.LegacyReplayRequest;
import com.evops.aqua.release.entity.LegacyPlayback;
import com.evops.aqua.release.entity.ReleaseChangeEvidence;
import com.evops.aqua.release.service.LegacyReplayService;
import com.evops.aqua.release.service.ReleaseEvidenceService;
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
 * 旧数据回放与变更证据：
 * 回放只读海水养殖旧台账（aqua-1）并给出可解释差异；证据可按对象/请求号/动作追溯。
 */
@RestController
@RequestMapping("/api/aqua/release")
public class ReleasePlaybackController {

    private final LegacyReplayService replayService;
    private final ReleaseEvidenceService evidenceService;

    public ReleasePlaybackController(LegacyReplayService replayService,
                                     ReleaseEvidenceService evidenceService) {
        this.replayService = replayService;
        this.evidenceService = evidenceService;
    }

    /** 用海水养殖管理旧数据回放：只读，重复 requestId 返回首次结果，差异可解释 */
    @PostMapping("/legacy-playback")
    public ApiResponse<LegacyPlayback> replay(@Validated @RequestBody LegacyReplayRequest request) {
        return ApiResponse.ok(replayService.replay(request));
    }

    @GetMapping("/legacy-playback")
    public ApiResponse<List<LegacyPlayback>> listPlayback() {
        return ApiResponse.ok(replayService.list());
    }

    @GetMapping("/legacy-playback/{id}")
    public ApiResponse<LegacyPlayback> playbackDetail(@PathVariable Long id) {
        return ApiResponse.ok(replayService.getOrThrow(id));
    }

    /** 变更证据：按对象类型/对象id/请求号/动作追溯 */
    @GetMapping("/evidence")
    public ApiResponse<List<ReleaseChangeEvidence>> evidence(@RequestParam(required = false) String entityType,
                                                             @RequestParam(required = false) Long entityId,
                                                             @RequestParam(required = false) String requestId,
                                                             @RequestParam(required = false) String action) {
        return ApiResponse.ok(evidenceService.list(entityType, entityId, requestId, action));
    }
}
