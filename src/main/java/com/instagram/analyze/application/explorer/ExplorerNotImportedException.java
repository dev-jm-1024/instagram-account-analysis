package com.instagram.analyze.application.explorer;

/**
 * 임포트 루트가 설정되지 않은 상태에서 탐색기를 요청했을 때 던진다 (domain_exception G5).
 * HTTP 매핑: {@code EXPLORER_NOT_IMPORTED} → 409 (GlobalExceptionHandler).
 *
 * <p>방어적 신호다 — Explorer 는 {@code requireCompleted()} 로 게이트되고 루트는 markInProgress 에서
 * 캡처되므로, COMPLETED 인데 루트가 없는 상황은 정상 흐름에선 발생하지 않는다.
 */
public class ExplorerNotImportedException extends RuntimeException {

    public ExplorerNotImportedException() {
        super("import root is not set");
    }
}
