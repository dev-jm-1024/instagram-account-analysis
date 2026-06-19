package com.instagram.analyze.api.activity;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.application.activity.ActivityService;
import com.instagram.analyze.domain.activity.ActivityType;
import com.instagram.analyze.domain.activity.dto.ActivityResponse;

/** GET /api/activity?type=post|like|comment|saved (부록 A). */
@RestController
@RequestMapping("/api/activity")
public class ActivityController {

    private final ActivityService activityService;
    private final ActivityAssembler assembler;

    public ActivityController(ActivityService activityService, ActivityAssembler assembler) {
        this.activityService = activityService;
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<ActivityResponse> activity(@RequestParam("type") ActivityType type) {
        return ApiResponse.ok(assembler.toResponse(type, activityService.count(type), activityService.monthlyCounts(type)));
    }
}
