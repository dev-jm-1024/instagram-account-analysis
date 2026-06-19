package com.instagram.analyze.domain.log;

import java.util.Map;

import com.instagram.analyze.domain.common.vo.EpochMillis;

import lombok.Getter;

/**
 * 고정 스키마 없는 범용 로그 한 행 (domain.md 8절).
 *
 * <p>최상위 키를 컬럼명으로, 값을 문자열로 변환해 보관한다. timestamp 로 인식 가능한 필드가
 * 있을 때만 채워지므로 nullable 이며, 그래서 {@code Timestamped} 를 구현하지 않는다.
 * fields 는 방어복사하여 불변으로 노출한다.
 */
@Getter
public final class LogRecord {

    private final Map<String, String> fields;
    private final EpochMillis timestamp; // nullable

    public LogRecord(Map<String, String> fields, EpochMillis timestamp) {
        this.fields = fields == null ? Map.of() : Map.copyOf(fields);
        this.timestamp = timestamp;
    }
}
