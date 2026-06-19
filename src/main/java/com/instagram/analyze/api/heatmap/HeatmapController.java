package com.instagram.analyze.api.heatmap;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.application.heatmap.HeatmapService;
import com.instagram.analyze.domain.heatmap.dto.HeatmapResponse;

/** GET /api/heatmap (부록 A). 미완료 시 서비스가 던져 503 매핑. */
@RestController
@RequestMapping("/api/heatmap")
public class HeatmapController {

    private final HeatmapService heatmapService;
    private final HeatmapAssembler assembler;

    public HeatmapController(HeatmapService heatmapService, HeatmapAssembler assembler) {
        this.heatmapService = heatmapService;
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<HeatmapResponse> heatmap() {
        return ApiResponse.ok(assembler.toResponse(heatmapService.heatmap()));
    }
}
