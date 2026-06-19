package com.instagram.analyze.domain.common.vo;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 전 도메인 timestamp 통일 단위(epoch milliseconds) VO.
 *
 * <ul>
 *   <li>{@link #normalize(long)} : connections(초)/messages(ms) 혼재를 ms 로 통일 (domain.md 1.3)</li>
 *   <li>{@link #of(long)} : 0 이하 값을 fail-fast 거부 → 도메인 내부엔 항상 유효한 값만 존재
 *       (null/0 이하 필터링은 Service 경계에서 PARSE_TIMESTAMP_INVALID 로 처리)</li>
 *   <li>{@link #dayOfWeekIndex(ZoneId)} : java.time(MON=1) → 0=월 기준 단일 변환 (히트맵 off-by-one 방지)</li>
 * </ul>
 */
@Getter
@EqualsAndHashCode
public final class EpochMillis {

    /** 초 단위 판별 기준: 이 값 미만이면 초로 간주하여 ×1000 (domain.md 1.3). */
    private static final long SECONDS_THRESHOLD = 10_000_000_000L;

    private final long value;

    private EpochMillis(long value) {
        this.value = value;
    }

    /** 정규화·검증된 epoch ms 를 생성한다. 0 이하는 거부한다. */
    public static EpochMillis of(long epochMillis) {
        if (epochMillis <= 0) {
            throw new IllegalArgumentException("epochMillis must be positive: " + epochMillis);
        }
        return new EpochMillis(epochMillis);
    }

    /** 초/밀리초 혼재 원시값을 ms 로 정규화한 뒤 생성한다 (domain.md 1.3). */
    public static EpochMillis normalize(long raw) {
        long millis = raw < SECONDS_THRESHOLD ? raw * 1000L : raw;
        return of(millis);
    }

    public LocalDateTime toLocalDateTime(ZoneId zone) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(value), zone);
    }

    /** 0=월 ... 6=일 (히트맵 grid 인덱스 기준). 변환은 이 한 곳에만 둔다. */
    public int dayOfWeekIndex(ZoneId zone) {
        return toLocalDateTime(zone).getDayOfWeek().getValue() - 1; // MON(1)..SUN(7) -> 0..6
    }

    public DayOfWeek dayOfWeek(ZoneId zone) {
        return toLocalDateTime(zone).getDayOfWeek();
    }

    /** 0~23 */
    public int hourOfDay(ZoneId zone) {
        return toLocalDateTime(zone).getHour();
    }
}
