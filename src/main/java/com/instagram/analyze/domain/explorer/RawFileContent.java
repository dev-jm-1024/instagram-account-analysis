package com.instagram.analyze.domain.explorer;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 단일 파일 원본 JSON (domain.md 10절).
 * 구조(키·계층·배열)는 raw 로 보존하되 문자열 값은 String Normalizer 로 보정한다.
 * 응답 크기가 10MB 를 초과하면 처음 10MB 만 담고 truncated=true.
 */
@Getter
@AllArgsConstructor
public final class RawFileContent {
    private final String path;
    private final String content;
    private final boolean truncated;
}
