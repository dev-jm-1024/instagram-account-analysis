package com.instagram.analyze.application.imports;

/**
 * 업로드/압축 해제가 진행 중(SAVING·EXTRACTING)일 때 import 가 들어오면 던진다 (A2 방어 가드).
 *
 * <p>추출이 끝나지 않은 디렉토리를 import 가 스캔하면 절반만 풀린 데이터가 섞인다. 프론트가
 * upload/status COMPLETED 폴링으로 게이팅하지만, 백엔드 단독으로도 안전하도록 막는다.
 * HTTP 매핑은 {@code api.GlobalExceptionHandler} 에서 {@code UPLOAD_IN_PROGRESS(409)} 로 부여한다.
 */
public class UploadInProgressException extends RuntimeException {

    public UploadInProgressException() {
        super("upload/extraction in progress");
    }
}
