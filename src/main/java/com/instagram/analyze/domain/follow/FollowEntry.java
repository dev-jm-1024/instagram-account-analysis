package com.instagram.analyze.domain.follow;

import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.common.vo.Username;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 팔로우 관계 단건 (domain.md 2절).
 *
 * <p>username 이 1차 키이고 timestamp(팔로우 시각)는 부차 메타데이터다. close_friends/restricted/
 * pending 등은 실제 export 에서 timestamp 가 없을 수 있으므로 <b>timestamp 는 nullable</b> 이며,
 * 불량이어도 관계 항목은 유지한다(드롭 시 탭이 비고 맞팔·짝사랑 집합연산이 왜곡됨).
 * 그래서 {@code Timestamped} 를 구현하지 않는다(활동 timestamp 소스가 아님 — 히트맵·활동기간에서 제외).
 */
@Getter
@AllArgsConstructor
public class FollowEntry {
    private final EpochMillis timestamp;   // nullable (팔로우 시각 미상 가능)
    private final Username username;
    private final FollowRelationType relationType;
}
