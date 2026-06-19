package com.instagram.analyze.api.activity;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.activity.ActivityType;
import com.instagram.analyze.domain.activity.dto.ActivityResponse;

@Component
public class ActivityAssembler {

    public ActivityResponse toResponse(ActivityType type, long total, Map<YearMonth, Long> monthly) {
        List<ActivityResponse.MonthlyCount> counts = monthly.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new ActivityResponse.MonthlyCount(e.getKey().toString(), e.getValue()))
                .toList();
        return new ActivityResponse(type.name().toLowerCase(), total, counts);
    }
}
