package com.instagram.analyze.domain.follow;

/**
 * 팔로우 조회 필터 (domain.md 2절, 부록 A {@code GET /api/follows?type=...}).
 *
 * <ul>
 *   <li>{@link #FOLLOWERS} : 전체 팔로워 (followers 전수), {@link #FOLLOWING} : 전체 팔로잉 (following 전수)</li>
 *   <li>{@link #MUTUAL} : 맞팔 (following ∩ followers)</li>
 *   <li>{@link #I_FOLLOW_ONLY} : 내가 짝사랑 (following − followers)</li>
 *   <li>{@link #FOLLOWS_ME_ONLY} : 나를 짝사랑 (followers − following)</li>
 *   <li>나머지는 {@link FollowRelationType} 과 <b>의미상 대응</b>한다 — 단, enum 이름이 다르므로
 *       ({@code UNFOLLOWED}↔{@code RECENTLY_UNFOLLOWED}, {@code PENDING}↔{@code PENDING_REQUEST},
 *       {@code CLOSE_FRIENDS}↔{@code CLOSE_FRIEND}) {@code name()} 자동 매핑은 불가하다.
 *       QueryType → RelationType 변환은 {@code FollowService} 의 책임(명시적 switch/Map)이다.</li>
 * </ul>
 */
public enum FollowQueryType {
    FOLLOWERS,            // 전체 팔로워 (relationships_followers 전수)
    FOLLOWING,            // 전체 팔로잉 (relationships_following 전수)
    MUTUAL,
    I_FOLLOW_ONLY,
    FOLLOWS_ME_ONLY,
    UNFOLLOWED,
    PENDING,
    CLOSE_FRIENDS,
    RESTRICTED,
    ALL
}
