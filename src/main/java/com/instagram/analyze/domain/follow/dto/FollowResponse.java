package com.instagram.analyze.domain.follow.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/follows?type=...} 응답: 통계 + type 으로 필터된 항목 목록.
 */
@Getter
public class FollowResponse {

    private final int followerCount;
    private final int followingCount;
    private final int mutualCount;
    private final int iFollowOnlyCount;
    private final int followsMeOnlyCount;
    private final List<FollowItem> items;

    public FollowResponse(int followerCount, int followingCount, int mutualCount,
                          int iFollowOnlyCount, int followsMeOnlyCount, List<FollowItem> items) {
        this.followerCount = followerCount;
        this.followingCount = followingCount;
        this.mutualCount = mutualCount;
        this.iFollowOnlyCount = iFollowOnlyCount;
        this.followsMeOnlyCount = followsMeOnlyCount;
        this.items = items == null ? List.of() : List.copyOf(items);
    }

    @Getter
    @AllArgsConstructor
    public static class FollowItem {
        private final String username;
        private final Long followedAt; // epoch ms, nullable (팔로우 시각 미상 가능)
    }
}
