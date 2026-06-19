package com.instagram.analyze.api.error;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 표준 에러 응답 (domain_exception §1).
 */
@Getter
@AllArgsConstructor
public class ErrorResponse {
    private final String code;       // 프론트 분기 기준
    private final String message;    // 사용자 안내(한국어)
    private final String detail;     // 개발·디버그용 (nullable)
    private final int httpStatus;
}
