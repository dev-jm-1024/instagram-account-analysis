package com.instagram.analyze.domain.common;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.instagram.analyze.domain.common.vo.EpochMillis;

/**
 * epoch ms timestamp 를 가진 도메인 레코드의 공통 동작.
 *
 * <p>필드를 갖지 않는 인터페이스로 두어 자식이 {@code @Getter @AllArgsConstructor} 만으로 구현하게 한다.
 * 요일/시 변환은 {@link EpochMillis} 한 곳에 위임하여 도메인 간 일관성을 보장한다 (domain.md 부록 C).
 */
public interface Timestamped {

    EpochMillis getTimestamp();

    default LocalDateTime toLocalDateTime(ZoneId zone) {
        return getTimestamp().toLocalDateTime(zone);
    }

    /** 0=월 ... 6=일 (히트맵 grid 인덱스 기준). */
    default int dayOfWeekIndex(ZoneId zone) {
        return getTimestamp().dayOfWeekIndex(zone);
    }

    default DayOfWeek dayOfWeek(ZoneId zone) {
        return getTimestamp().dayOfWeek(zone);
    }

    /** 0~23 */
    default int hourOfDay(ZoneId zone) {
        return getTimestamp().hourOfDay(zone);
    }
}
