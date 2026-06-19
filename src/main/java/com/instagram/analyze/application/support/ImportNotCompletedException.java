package com.instagram.analyze.application.support;

/**
 * 임포트 미완료 상태에서 완료 전제 조회를 요청했을 때 던진다 (interface_plan §3.2, G3).
 *
 * <p>HTTP 매핑({@code IMPORT_NOT_COMPLETED} → 503)은 api 계층 ControllerAdvice 에서 부여한다(예외 배선 단계).
 */
public class ImportNotCompletedException extends RuntimeException {

    public ImportNotCompletedException() {
        super("import is not COMPLETED");
    }
}
