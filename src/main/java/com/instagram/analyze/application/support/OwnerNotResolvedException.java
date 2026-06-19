package com.instagram.analyze.application.support;

/**
 * 본인식별 미해결 상태에서 owner 의존 통계(DM)를 요청했을 때 던진다 (domain_exception G2).
 * HTTP 매핑: {@code DM_OWNER_NOT_RESOLVED} → 409 (GlobalExceptionHandler).
 */
public class OwnerNotResolvedException extends RuntimeException {

    public OwnerNotResolvedException() {
        super("account owner is not resolved");
    }
}
