package com.instagram.analyze.domain.imports;

/**
 * 임포트 진행 상태 (domain.md 1절).
 * 멀티유저 동기화용이 아니라 프론트가 진행률·완료 여부를 표시하기 위한 단순 상태값이다.
 */
public enum ImportStatus {
    IDLE,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
