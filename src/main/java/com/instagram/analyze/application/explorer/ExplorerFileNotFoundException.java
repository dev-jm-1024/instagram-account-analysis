package com.instagram.analyze.application.explorer;

/**
 * 요청 파일이 존재하지 않을 때 던진다 (domain_exception G5).
 * HTTP 매핑: {@code EXPLORER_FILE_NOT_FOUND} → 404 (예외 배선 단계).
 */
public class ExplorerFileNotFoundException extends RuntimeException {

    public ExplorerFileNotFoundException(String path) {
        super("file does not exist at requested path: " + path);
    }
}
