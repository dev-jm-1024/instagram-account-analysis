package com.instagram.analyze.domain.follow;

import java.util.List;

import lombok.Getter;

/**
 * 팔로우 교차 분석 결과 (domain.md 2절).
 *
 * <p>결과를 {@code List<Username>} 이 아닌 {@code List<FollowEntry>} 로 보유하여 username 과
 * 팔로우 시각을 함께 유지한다 (domain_io 4절 "username + 팔로우 시각" 충족, 시각순 정렬 가능).
 * 모든 리스트는 방어복사하여 불변으로 노출한다.
 */
@Getter
public final class FollowAnalysis {

    private final int followerCount;
    private final int followingCount;
    private final List<FollowEntry> mutual;        // following ∩ followers
    private final List<FollowEntry> iFollowOnly;   // following - followers (내가 짝사랑)
    private final List<FollowEntry> followsMeOnly; // followers - following (나를 짝사랑)

    public FollowAnalysis(int followerCount, int followingCount, List<FollowEntry> mutual,
                          List<FollowEntry> iFollowOnly, List<FollowEntry> followsMeOnly) {
        this.followerCount = followerCount;
        this.followingCount = followingCount;
        this.mutual = mutual == null ? List.of() : List.copyOf(mutual);
        this.iFollowOnly = iFollowOnly == null ? List.of() : List.copyOf(iFollowOnly);
        this.followsMeOnly = followsMeOnly == null ? List.of() : List.copyOf(followsMeOnly);
    }
}
