package com.instagram.analyze.domain.common.vo;

import com.instagram.analyze.domain.common.ParseWarningCode;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 파싱 중 단일 항목 스킵 시 누적되는 내부 경고 (domain_exception G6).
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class ParseWarning {
    private final ParseWarningCode code;
    private final String source;  // 경고가 발생한 파일/위치
    private final String detail;
}
