package com.instagram.analyze.api.common;

import lombok.Getter;

/**
 * 조회 엔드포인트 공통 응답 envelope. 9개 메뉴가 같은 모양({@code code, message, data})을 갖는다.
 *
 * <ul>
 *   <li>정상: {@code code=null, message=null, data=...}</li>
 *   <li>G4(데이터 없음, HTTP 200): {@code code=SEARCH_HISTORY_NOT_FOUND 등, data=빈 결과}</li>
 * </ul>
 * 에러(4xx/5xx)는 이 envelope 가 아니라 {@code api.error.ErrorResponse} 로 내려간다.
 */
@Getter
public final class ApiResponse<T> {

    private final String code;
    private final String message;
    private final T data;

    private ApiResponse(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(null, null, data);
    }

    public static <T> ApiResponse<T> of(ApiResultCode code, T data) {
        return new ApiResponse<>(code.name(), code.getMessage(), data);
    }
}
