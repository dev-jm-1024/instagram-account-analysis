package com.instagram.analyze.domain.activity.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/activity?type=post|like|comment|saved} 응답: 월별 집계(메타데이터만).
 */
@Getter
public class ActivityResponse {

    private final String type;
    private final long total;
    private final List<MonthlyCount> monthlyCounts;

    public ActivityResponse(String type, long total, List<MonthlyCount> monthlyCounts) {
        this.type = type;
        this.total = total;
        this.monthlyCounts = monthlyCounts == null ? List.of() : List.copyOf(monthlyCounts);
    }

    @Getter
    @AllArgsConstructor
    public static class MonthlyCount {
        private final String month; // yyyy-MM
        private final long count;
    }
}
