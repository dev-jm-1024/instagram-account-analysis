package com.instagram.analyze.domain.heatmap.dto;

import com.instagram.analyze.domain.heatmap.vo.HeatmapPeak;

import lombok.Getter;

/**
 * {@code GET /api/heatmap} 응답: int[7][24] 그리드 + peak.
 */
@Getter
public class HeatmapResponse {

    private final int[][] grid;
    private final HeatmapPeak peak;

    public HeatmapResponse(int[][] grid, HeatmapPeak peak) {
        this.grid = deepCopy(grid);
        this.peak = peak;
    }

    public int[][] getGrid() {
        return deepCopy(grid);
    }

    private static int[][] deepCopy(int[][] src) {
        if (src == null) {
            return new int[7][24];
        }
        int[][] copy = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            copy[i] = src[i].clone();
        }
        return copy;
    }
}
