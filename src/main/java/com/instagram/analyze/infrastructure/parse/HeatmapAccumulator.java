package com.instagram.analyze.infrastructure.parse;

import java.time.ZoneId;

import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.heatmap.ActivityHeatmap;
import com.instagram.analyze.domain.heatmap.vo.HeatmapPeak;

/**
 * 히트맵 사전계산 누적기 (domain.md 5). import 시점에 5종 timestamp 를 받아 7×24 그리드 + peak 산출.
 * 요일 인덱스는 0=월(EpochMillis.dayOfWeekIndex). 데이터 없으면 peak=null(grid 는 빈 7×24).
 */
public final class HeatmapAccumulator {

    private final ZoneId zone;
    private final int[][] grid = new int[7][24];

    public HeatmapAccumulator(ZoneId zone) {
        this.zone = zone;
    }

    public void add(EpochMillis timestamp) {
        if (timestamp == null) {
            return;
        }
        grid[timestamp.dayOfWeekIndex(zone)][timestamp.hourOfDay(zone)]++;
    }

    public ActivityHeatmap build() {
        int max = 0;
        int peakDay = 0;
        int peakHour = 0;
        for (int d = 0; d < 7; d++) {
            for (int h = 0; h < 24; h++) {
                if (grid[d][h] > max) {
                    max = grid[d][h];
                    peakDay = d;
                    peakHour = h;
                }
            }
        }
        HeatmapPeak peak = max > 0 ? new HeatmapPeak(peakDay, peakHour, max) : null;
        return new ActivityHeatmap(grid, peak);
    }
}
