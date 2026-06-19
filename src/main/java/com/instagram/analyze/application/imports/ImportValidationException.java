package com.instagram.analyze.application.imports;

/**
 * 임포트 경로·포맷 검증 실패(domain_exception G1). reason 으로 구체 사유를 구분한다.
 * HTTP 매핑은 api.GlobalExceptionHandler 에서 reason → ErrorCode 로 부여한다.
 */
public class ImportValidationException extends RuntimeException {

    public enum Reason {
        PATH_NOT_FOUND,
        PATH_NOT_DIRECTORY,
        NOT_INSTAGRAM_EXPORT,
        HTML_ONLY
    }

    private final transient Reason reason;

    public ImportValidationException(Reason reason, String detail) {
        super(detail);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}
