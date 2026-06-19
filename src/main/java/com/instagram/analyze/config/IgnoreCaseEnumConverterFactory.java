package com.instagram.analyze.config;

import java.util.Locale;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

/**
 * 쿼리 파라미터 enum 바인딩을 대소문자 무시로 처리한다 — {@code ?type=post} == {@code ?type=POST}.
 *
 * <p>실데이터 검증(2026-06-05)에서 도메인 문서·프론트 계약이 소문자({@code post|like|...})인데 enum 은
 * 대문자라 {@code ?type=post} 가 400 으로 깨지는 것을 확인 → 양쪽을 모두 받는다. 알 수 없는 값은
 * {@code Enum.valueOf} 가 {@code IllegalArgumentException} → 변환 실패 → {@code VALIDATION_FAILED(400)} 유지.
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class IgnoreCaseEnumConverterFactory implements ConverterFactory<String, Enum> {

    @Override
    public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
        return source -> (T) Enum.valueOf(targetType, source.trim().toUpperCase(Locale.ROOT));
    }
}
