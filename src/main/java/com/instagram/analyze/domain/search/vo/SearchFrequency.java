package com.instagram.analyze.domain.search.vo;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 검색어 빈도 집계 결과 (domain.md 6절). 빈도 내림차순 정렬에 사용한다.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public final class SearchFrequency {
    private final Keyword keyword;
    private final long count;
}
