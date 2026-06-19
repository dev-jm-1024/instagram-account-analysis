package com.instagram.analyze.domain.overview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/overview} 응답.
 * 임포트 미완료 시 importRequired=true, data=null (domain_exception 5.1).
 */
@Getter
public class OverviewResponse {

    private final boolean importRequired;
    private final OverviewData data; // 미완료 시 null

    public OverviewResponse(boolean importRequired, OverviewData data) {
        this.importRequired = importRequired;
        this.data = data;
    }

    /** 임포트 미완료 응답 (200 + importRequired:true + data:null). */
    public static OverviewResponse importRequired() {
        return new OverviewResponse(true, null);
    }

    @Getter
    @AllArgsConstructor
    public static class OverviewData {
        private final int followerCount;
        private final int followingCount;
        private final int mutualCount;
        private final int iFollowOnlyCount;
        private final int totalPostCount;
        private final int conversationCount;
        private final long messageCount;
        private final long likeCount;
        private final long commentCount;
        private final Long activityFrom; // epoch ms, nullable
        private final Long activityTo;   // epoch ms, nullable
        private final String mostActiveMonth;
    }
}
