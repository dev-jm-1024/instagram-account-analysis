package com.instagram.analyze.domain.heatmap.vo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 히트맵 최댓값 셀 (domain.md 5절).
 * dayOfWeek 는 0=월 ... 6=일 (EpochMillis.dayOfWeekIndex 기준), hour 는 0~23.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class HeatmapPeak {
    private final int dayOfWeek;
    private final int hour;
    private final int count;
}
