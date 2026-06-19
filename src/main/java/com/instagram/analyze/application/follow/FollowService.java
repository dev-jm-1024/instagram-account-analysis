package com.instagram.analyze.application.follow;

import java.util.List;

import com.instagram.analyze.domain.follow.FollowAnalysis;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.follow.FollowQueryType;

/**
 * 팔로우 관계(2) 조회 서비스 (interface_plan §4.2).
 *
 * <p>전제: {@code requireCompleted()}.
 * <p>비교는 {@code Username} 소문자 정규화 equals(맞팔 집합연산). 결과는 {@code FollowEntry} 라
 * 팔로우 시각이 보존된다.
 */
public interface FollowService {

    /** 맞팔(∩)·내가 짝사랑(차집합)·나를 짝사랑(차집합) 교차 분석. */
    FollowAnalysis analyze();

    /**
     * type 으로 필터된 팔로우 목록. {@code GET /api/follows?type=...}
     *
     * <p>{@code FollowQueryType → FollowRelationType} 변환은 <b>이 서비스의 책임</b>이다 —
     * enum 이름이 1:1이 아니므로({@code UNFOLLOWED↔RECENTLY_UNFOLLOWED},
     * {@code PENDING↔PENDING_REQUEST}, {@code CLOSE_FRIENDS↔CLOSE_FRIEND}) 명시적 매핑이 필요하다.
     * MUTUAL/I_FOLLOW_ONLY/FOLLOWS_ME_ONLY 는 {@link #analyze()} 결과를 사용한다.
     * {@code ALL} 은 전 관계 타입 {@code FollowEntry} 의 합집합(필터 없음)을 반환한다.
     */
    List<FollowEntry> findByType(FollowQueryType type);
}
