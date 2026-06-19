package com.instagram.analyze.domain.overview;

import com.instagram.analyze.domain.common.vo.EpochMillis;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 개요 대시보드 집계 (domain.md 9절). 타 도메인 카운트를 조합한 결과.
 * 임포트 미완료 시 importRequired=true 이며 나머지 수치는 의미가 없다.
 */
@Getter
@AllArgsConstructor
public final class OverviewSummary {

    private final int followerCount;
    private final int followingCount;
    private final int mutualCount;
    private final int iFollowOnlyCount;      // 내가 짝사랑
    private final int totalPostCount;        // post + story + reels
    private final int conversationCount;     // DM 대화방 수
    private final long messageCount;
    private final long likeCount;
    private final long commentCount;
    private final EpochMillis activityFrom;   // 가장 오래된 timestamp (nullable)
    private final EpochMillis activityTo;     // 가장 최근 timestamp (nullable)
    private final String mostActiveMonth;     // yyyy-MM
    private final boolean importRequired;
}
