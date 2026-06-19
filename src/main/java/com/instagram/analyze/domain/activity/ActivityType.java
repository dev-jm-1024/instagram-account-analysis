package com.instagram.analyze.domain.activity;

/**
 * 활동 조회 타입 (domain.md 4절, 부록 A {@code GET /api/activity?type=...}).
 * POST 는 게시물+스토리+릴스 합산을 의미한다.
 */
public enum ActivityType {
    POST,
    LIKE,
    COMMENT,
    SAVED
}
