package com.instagram.analyze.infrastructure.text;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;

/**
 * {@link StringNormalizer} 구현 (domain.md 1.2). ISO-8859-1 → UTF-8 재디코딩으로 mojibake 보정.
 *
 * <p>이미 올바른 유니코드(코드포인트 > 0xFF 포함)는 재디코딩하면 손상되므로 그대로 둔다.
 * 이 가드 덕분에 normalize 가 정상 UTF-8 에 대해 안전(idempotent)하다 — Explorer 가 raw 파일에
 * 적용해도 한글이 깨지지 않는다. 디코딩 불가 시 원본 유지(PARSE_STRING_DECODE_FAILED).
 */
@Component
public class DefaultStringNormalizer implements StringNormalizer {

    @Override
    public String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        // 0xFF 초과 문자가 있으면 이미 정상 유니코드 → 재디코딩 금지(손상 방지)
        for (int i = 0; i < raw.length(); i++) {
            if (raw.charAt(i) > 0xFF) {
                return raw;
            }
        }
        byte[] bytes = raw.getBytes(StandardCharsets.ISO_8859_1);
        // strict 디코딩: 유효한 UTF-8 일 때만 변환, 아니면 원본 유지(lenient 의 U+FFFD 손상 방지)
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return raw;   // 유효한 UTF-8 아님 → 원본 유지 (PARSE_STRING_DECODE_FAILED)
        }
    }
}
