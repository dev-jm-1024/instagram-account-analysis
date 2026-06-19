package com.instagram.analyze.api.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.instagram.analyze.application.explorer.ExplorerFileNotFoundException;
import com.instagram.analyze.application.explorer.ExplorerNotImportedException;
import com.instagram.analyze.application.explorer.ExplorerPathOutOfRootException;
import com.instagram.analyze.application.imports.ImportReimportRequiredException;
import com.instagram.analyze.application.imports.ImportValidationException;
import com.instagram.analyze.application.imports.UploadInProgressException;
import com.instagram.analyze.application.support.ImportNotCompletedException;
import com.instagram.analyze.application.support.OwnerNotResolvedException;

/**
 * application 예외 → HTTP 매핑 (interface_plan §9 추적 항목 일괄 정리).
 *
 * <p>application 계층은 HTTP 를 모르는 도메인 예외만 던지고, 여기서 {@link ErrorCode} 로 매핑한다.
 * 임포트 경로/포맷 검증(NOT_FOUND·NOT_INSTAGRAM_EXPORT 등, G1)은 실제 ETL(④) 도입 시 해당 예외와 함께 추가한다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ImportNotCompletedException.class)
    public ResponseEntity<ErrorResponse> handle(ImportNotCompletedException e) {
        return build(ErrorCode.IMPORT_NOT_COMPLETED, e.getMessage());
    }

    @ExceptionHandler(OwnerNotResolvedException.class)
    public ResponseEntity<ErrorResponse> handle(OwnerNotResolvedException e) {
        return build(ErrorCode.DM_OWNER_NOT_RESOLVED, e.getMessage());
    }

    @ExceptionHandler(ExplorerPathOutOfRootException.class)
    public ResponseEntity<ErrorResponse> handle(ExplorerPathOutOfRootException e) {
        return build(ErrorCode.EXPLORER_PATH_OUT_OF_ROOT, e.getMessage());
    }

    @ExceptionHandler(ExplorerFileNotFoundException.class)
    public ResponseEntity<ErrorResponse> handle(ExplorerFileNotFoundException e) {
        return build(ErrorCode.EXPLORER_FILE_NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ExplorerNotImportedException.class)
    public ResponseEntity<ErrorResponse> handle(ExplorerNotImportedException e) {
        return build(ErrorCode.EXPLORER_NOT_IMPORTED, e.getMessage());
    }

    /** @Valid 바인딩 실패 → 필드별 code 매핑(folderPath/username), 그 외 VALIDATION_FAILED. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String field = fieldError != null ? fieldError.getField() : "";
        String detail = fieldError != null ? fieldError.getDefaultMessage() : null;
        ErrorCode code = switch (field) {
            case "folderPath" -> ErrorCode.IMPORT_PATH_BLANK;
            case "username" -> ErrorCode.OWNER_INPUT_BLANK;
            default -> ErrorCode.VALIDATION_FAILED;
        };
        return build(code, detail);
    }

    @ExceptionHandler(ImportReimportRequiredException.class)
    public ResponseEntity<ErrorResponse> handle(ImportReimportRequiredException e) {
        return build(ErrorCode.IMPORT_REIMPORT_REQUIRED, e.getMessage());
    }

    @ExceptionHandler(UploadInProgressException.class)
    public ResponseEntity<ErrorResponse> handle(UploadInProgressException e) {
        return build(ErrorCode.UPLOAD_IN_PROGRESS, e.getMessage());
    }

    /** 임포트 경로·포맷 검증 실패(G1) → reason 별 코드 매핑. */
    @ExceptionHandler(ImportValidationException.class)
    public ResponseEntity<ErrorResponse> handle(ImportValidationException e) {
        ErrorCode code = switch (e.getReason()) {
            case PATH_NOT_FOUND -> ErrorCode.IMPORT_PATH_NOT_FOUND;
            case PATH_NOT_DIRECTORY -> ErrorCode.IMPORT_PATH_NOT_DIRECTORY;
            case NOT_INSTAGRAM_EXPORT -> ErrorCode.IMPORT_NOT_INSTAGRAM_EXPORT;
            case HTML_ONLY -> ErrorCode.IMPORT_HTML_ONLY;
        };
        return build(code, e.getMessage());
    }

    /** 쿼리 파라미터 타입 불일치(예: ?type=오타 → enum 변환 실패). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handle(MethodArgumentTypeMismatchException e) {
        return build(ErrorCode.VALIDATION_FAILED, e.getName() + " 값이 올바르지 않습니다: " + e.getValue());
    }

    /** 서비스 방어 검증 backstop. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handle(IllegalArgumentException e) {
        return build(ErrorCode.VALIDATION_FAILED, e.getMessage());
    }

    /** 최종 fallback — 위 구체 핸들러가 못 잡은 모든 예외 → 구조화된 500. 스택은 반드시 로깅. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handle(Exception e) {
        log.error("unhandled exception", e);
        return build(ErrorCode.INTERNAL_ERROR, e.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String detail) {
        return ResponseEntity.status(code.getHttpStatus()).body(code.toResponse(detail));
    }
}
