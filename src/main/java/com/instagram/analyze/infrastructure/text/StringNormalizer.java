package com.instagram.analyze.infrastructure.text;

/**
 * 문자열 정규화 (domain.md 1.2) — 인코딩 IO 이므로 {@code infrastructure} 에 둔다.
 *
 * <p>모든 문자열 필드를 {@code ISO-8859-1 → UTF-8} 재디코딩하여 한글·이모지 mojibake 를 보정한다.
 * 디코딩 불가 시 원본 값을 그대로 유지한다({@code PARSE_STRING_DECODE_FAILED} 경고).
 */
public interface StringNormalizer {

    /** 깨진 문자열을 정규화한다. 실패 시 원본을 반환한다. */
    String normalize(String raw);
}
