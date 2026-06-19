package com.instagram.analyze.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.instagram.analyze.infrastructure.text.DefaultStringNormalizer;

class DefaultStringNormalizerTest {

    private final DefaultStringNormalizer normalizer = new DefaultStringNormalizer();

    @Test
    void fixesMojibake() {
        String original = "안녕하세요";
        // UTF-8 바이트를 latin1 로 잘못 읽은 mojibake 상태를 재현
        String mojibake = new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        assertEquals(original, normalizer.normalize(mojibake));
    }

    @Test
    void leavesProperUnicodeUnchanged() {
        String proper = "안녕😀";   // 0xFF 초과 문자 → 가드로 그대로 반환(손상 없음)
        assertEquals(proper, normalizer.normalize(proper));
    }

    @Test
    void leavesAsciiUnchanged() {
        assertEquals("{\"k\":1}", normalizer.normalize("{\"k\":1}"));
    }

    @Test
    void preservesOriginalWhenNotValidUtf8() {
        // é(U+00E9) 단독: 모두 ≤0xFF 라 재디코딩 시도되지만 0xE9 단독은 유효한 UTF-8 이 아님
        // → strict 디코더가 실패 → 원본 유지(U+FFFD 손상 아님)
        String latin1 = "café";
        assertEquals(latin1, normalizer.normalize(latin1));
    }

    @Test
    void handlesNull() {
        assertNull(normalizer.normalize(null));
    }
}
