package com.instagram.analyze.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

/**
 * 쿼리 파라미터 enum 대소문자 무시 변환 (실데이터 검증 2026-06-05: ?type=post 가 400 나던 문제).
 */
class IgnoreCaseEnumConverterFactoryTest {

    enum Sample { POST, LIKE }

    private Converter<String, Sample> converter() {
        return new IgnoreCaseEnumConverterFactory().getConverter(Sample.class);
    }

    @Test
    void convertsLowercaseAndTrimmedToEnum() {
        assertEquals(Sample.POST, converter().convert("post"));
        assertEquals(Sample.LIKE, converter().convert(" LIKE "));
        assertEquals(Sample.POST, converter().convert("Post"));
    }

    @Test
    void unknownValueThrows_mappedTo400Upstream() {
        assertThrows(IllegalArgumentException.class, () -> converter().convert("nope"));
    }
}
