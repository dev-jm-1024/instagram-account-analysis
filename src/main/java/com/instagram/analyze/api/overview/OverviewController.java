package com.instagram.analyze.api.overview;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.application.overview.OverviewService;
import com.instagram.analyze.domain.overview.dto.OverviewResponse;

/** GET /api/overview (부록 A). 미완료여도 200 (data.importRequired=true). */
@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private final OverviewService overviewService;
    private final OverviewAssembler assembler;

    public OverviewController(OverviewService overviewService, OverviewAssembler assembler) {
        this.overviewService = overviewService;
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<OverviewResponse> overview() {
        return ApiResponse.ok(assembler.toResponse(overviewService.overview()));
    }
}
