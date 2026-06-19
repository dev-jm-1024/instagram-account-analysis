package com.instagram.analyze.api.common;

import lombok.Getter;

/**
 * 비-에러 body 상태 코드 (domain_exception G4, HTTP 200). 에러 코드({@code api.error.ErrorCode})와 분리.
 */
@Getter
public enum ApiResultCode {

    SEARCH_HISTORY_NOT_FOUND("검색 기록 데이터가 없습니다."),
    LOGIN_HISTORY_NOT_FOUND("로그인 기록 데이터가 없습니다."),
    MISC_LOG_DIR_NOT_FOUND("로그 데이터가 없습니다.");

    private final String message;

    ApiResultCode(String message) {
        this.message = message;
    }
}
