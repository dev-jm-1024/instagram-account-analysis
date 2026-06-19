package com.instagram.analyze.application.support;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * "데이터 없음(G4)" 신호를 담는 결과 래퍼 (interface_plan §3.3).
 *
 * <p>보관소는 primitive 사실({@code ImportReadStore.sourceExists}) 만 제공하고, 서비스가 그 boolean 으로
 * 결과를 이 타입으로 래핑하여 API 계약화한다. {@code sourceExists == false} 면 Assembler 가
 * {@code *_NOT_FOUND} code 를 부여한다 (HTTP 200 + 빈 컬렉션).
 *
 * @param <T> 담는 값 (소스가 없어도 null 이 아닌 빈 컬렉션을 권장)
 */
@Getter
@AllArgsConstructor
public final class Sourced<T> {

    private final boolean sourceExists;
    private final T value;

    /** 소스 파일이 존재하는 경우. */
    public static <T> Sourced<T> present(T value) {
        return new Sourced<>(true, value);
    }

    /** 소스 파일이 없는 경우 (빈 결과 + NOT_FOUND code 유도). */
    public static <T> Sourced<T> absent(T emptyValue) {
        return new Sourced<>(false, emptyValue);
    }
}
