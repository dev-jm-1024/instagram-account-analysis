package com.instagram.analyze.application.explorer;

/**
 * 요청 경로가 임포트 루트 외부를 가리킬 때 던진다 (domain_exception G5).
 * HTTP 매핑: {@code EXPLORER_PATH_OUT_OF_ROOT} → 400 (예외 배선 단계).
 */
public class ExplorerPathOutOfRootException extends RuntimeException {

    public ExplorerPathOutOfRootException(String path) {
        super("requested path is outside import root: " + path);
    }
}
