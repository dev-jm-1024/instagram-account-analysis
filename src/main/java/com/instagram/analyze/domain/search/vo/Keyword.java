package com.instagram.analyze.domain.search.vo;

import lombok.Getter;

/**
 * 검색어 VO (domain.md 6절).
 *
 * <p>빈도 집계의 키이며 equals/hashCode 로 동일 검색어를 묶는다.
 * 문자열 정규화(String Normalizer, 1.2)는 파싱 단계에서 적용된 값을 받는다고 가정한다.
 */
@Getter
public final class Keyword {

    private final String value;

    private Keyword(String value) {
        this.value = value;
    }

    public static Keyword of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("keyword must not be blank");
        }
        return new Keyword(value.trim());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Keyword other)) {
            return false;
        }
        return value.equals(other.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
