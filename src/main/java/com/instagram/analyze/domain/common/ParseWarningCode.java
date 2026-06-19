package com.instagram.analyze.domain.common;

/**
 * 파싱 중 항목 스킵 시 누적되는 내부 경고 코드 (domain_exception G6).
 * HTTP 에러가 아니라 전체 파싱을 멈추지 않고 항목만 스킵한다.
 */
public enum ParseWarningCode {
    /** 필수 키 누락 또는 예상 외 구조 → 항목/대화방 스킵 */
    SCHEMA_MISMATCH,
    /** timestamp 가 null 또는 0 이하 → 항목 스킵 */
    TIMESTAMP_INVALID,
    /** username·sender_name 등 필수 문자열이 null/빈 문자열 → 항목 스킵 */
    VALUE_BLANK,
    /** string_list_data 배열이 null 또는 빈 배열 → 항목 스킵 */
    STRING_LIST_EMPTY,
    /** JSON 형식 오류 → 파일 스킵, "파싱 실패" 표시 */
    JSON_ERROR,
    /** 파일 크기 0 → 파일 스킵 */
    FILE_EMPTY,
    /**
     * ISO-8859-1 → UTF-8 재디코딩 불가 → 원본 값 유지.
     * 예약 코드 — StringNormalizer 는 실패 시 조용히 원본을 유지하므로(단일 사용자 정책 a) 현재 발생 지점 없음.
     */
    STRING_DECODE_FAILED
}
