package com.instagram.analyze.api.error;

import lombok.Getter;

/**
 * 예외 코드 ↔ HTTP status ↔ 사용자 메시지 단일 정의 (domain_exception §9).
 * application 계층은 HTTP 를 모르고, 여기(api)에서만 매핑한다.
 */
@Getter
public enum ErrorCode {

    // G1 — 임포트 경로·포맷 (NOT_FOUND 이하는 실제 ETL(④)에서 throw 시 매핑)
    IMPORT_PATH_BLANK(400, "폴더 경로를 입력해주세요."),
    IMPORT_PATH_NOT_FOUND(400, "입력한 경로가 존재하지 않습니다."),
    IMPORT_PATH_NOT_DIRECTORY(400, "입력한 경로가 폴더가 아닙니다."),
    IMPORT_NOT_INSTAGRAM_EXPORT(422, "Instagram export 폴더가 아닙니다. JSON 포맷으로 재다운로드 후 시도해주세요."),
    IMPORT_HTML_ONLY(422, "HTML 포맷은 지원하지 않습니다. JSON 포맷으로 재다운로드해주세요."),

    // G2 — 본인 식별
    OWNER_INPUT_BLANK(400, "사용자 이름(username)을 입력해주세요."),
    DM_OWNER_NOT_RESOLVED(409, "본인 식별이 필요합니다. 사용자 이름을 먼저 입력해주세요."),
    IMPORT_REIMPORT_REQUIRED(409, "원본 폴더를 찾을 수 없습니다. 데이터를 다시 임포트해주세요."),
    UPLOAD_IN_PROGRESS(409, "업로드/압축 해제가 진행 중입니다. 완료 후 다시 시도해주세요."),

    // G3 — 임포트 미완료
    IMPORT_NOT_COMPLETED(503, "데이터 임포트를 먼저 진행해주세요."),

    // G4 — 데이터 미존재(HTTP 200)는 에러가 아니므로 여기 두지 않는다.
    //       → api.common.ApiResultCode 로 분리(공통 envelope 의 body code 로 사용).

    // G5 — 탐색기
    EXPLORER_PATH_OUT_OF_ROOT(400, "임포트 루트 폴더 외부 경로는 탐색할 수 없습니다."),
    EXPLORER_FILE_NOT_FOUND(404, "요청한 파일을 찾을 수 없습니다."),
    EXPLORER_NOT_IMPORTED(409, "데이터 임포트를 먼저 진행해주세요."),

    // 공통
    VALIDATION_FAILED(400, "입력값이 올바르지 않습니다."),
    INTERNAL_ERROR(500, "예기치 못한 오류가 발생했습니다.");

    private final int httpStatus;
    private final String message;

    ErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public ErrorResponse toResponse(String detail) {
        return new ErrorResponse(name(), message, detail, httpStatus);
    }
}
