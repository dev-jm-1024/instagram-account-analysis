package com.instagram.analyze.api.overview;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.overview.OverviewSummary;
import com.instagram.analyze.domain.overview.dto.OverviewResponse;

@Component
public class OverviewAssembler {

    public OverviewResponse toResponse(OverviewSummary summary) {
        if (summary.isImportRequired()) {
            return OverviewResponse.importRequired();
        }
        OverviewResponse.OverviewData data = new OverviewResponse.OverviewData(
                summary.getFollowerCount(),
                summary.getFollowingCount(),
                summary.getMutualCount(),
                summary.getIFollowOnlyCount(),
                summary.getTotalPostCount(),
                summary.getConversationCount(),
                summary.getMessageCount(),
                summary.getLikeCount(),
                summary.getCommentCount(),
                toMillis(summary.getActivityFrom()),
                toMillis(summary.getActivityTo()),
                summary.getMostActiveMonth());
        return new OverviewResponse(false, data);
    }

    private Long toMillis(EpochMillis value) {
        return value == null ? null : value.getValue();
    }
}
