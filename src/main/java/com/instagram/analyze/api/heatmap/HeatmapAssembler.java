package com.instagram.analyze.api.heatmap;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.heatmap.ActivityHeatmap;
import com.instagram.analyze.domain.heatmap.dto.HeatmapResponse;

@Component
public class HeatmapAssembler {

    /** grid 는 non-null 7×24, peak 는 빈 데이터에서 null 가능(그대로 통과 — JSON null). */
    public HeatmapResponse toResponse(ActivityHeatmap heatmap) {
        return new HeatmapResponse(heatmap.getGrid(), heatmap.getPeak());
    }
}
