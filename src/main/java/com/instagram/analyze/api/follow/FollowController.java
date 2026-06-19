package com.instagram.analyze.api.follow;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.application.follow.FollowService;
import com.instagram.analyze.domain.follow.FollowQueryType;
import com.instagram.analyze.domain.follow.dto.FollowResponse;

/** GET /api/follows?type=... (부록 A). */
@RestController
@RequestMapping("/api/follows")
public class FollowController {

    private final FollowService followService;
    private final FollowAssembler assembler;

    public FollowController(FollowService followService, FollowAssembler assembler) {
        this.followService = followService;
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<FollowResponse> follows(
            @RequestParam(name = "type", defaultValue = "ALL") FollowQueryType type) {
        return ApiResponse.ok(assembler.toResponse(followService.analyze(), followService.findByType(type)));
    }
}
