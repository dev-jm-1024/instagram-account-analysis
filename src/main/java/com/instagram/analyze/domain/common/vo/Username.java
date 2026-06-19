package com.instagram.analyze.domain.common.vo;

import java.util.Locale;

import lombok.Getter;

/**
 * 계정 username VO.
 *
 * <p>원본 표기는 보존하되 equals/hashCode 는 소문자 정규화 기준으로 비교한다
 * (맞팔/짝사랑 집합 연산용, domain.md 2절 "대소문자 구분 없이 비교").
 */
@Getter
public final class Username {

    private final String value;
    private final String normalized;

    private Username(String value) {
        this.value = value;
        this.normalized = value.toLowerCase(Locale.ROOT);
    }

    public static Username of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        return new Username(value.trim());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Username other)) {
            return false;
        }
        return normalized.equals(other.normalized);
    }

    @Override
    public int hashCode() {
        return normalized.hashCode();
    }

    @Override
    public String toString() {
        return value;
    }
}
