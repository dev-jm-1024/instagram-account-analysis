package com.instagram.analyze.domain.follow;

/**
 * 팔로우 관계 파일 구분 (domain.md 2절, 6종 루트 키).
 */
public enum FollowRelationType {
    FOLLOWING,            // relationships_following
    FOLLOWER,             // relationships_followers
    RECENTLY_UNFOLLOWED,  // relationships_unfollowed_users
    PENDING_REQUEST,      // relationships_follow_requests_sent
    CLOSE_FRIEND,         // relationships_close_friends
    RESTRICTED            // relationships_restricted_users
}
