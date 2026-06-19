package com.instagram.analyze.domain.login;

import com.instagram.analyze.domain.common.Timestamped;
import com.instagram.analyze.domain.common.vo.EpochMillis;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 로그인/로그아웃 이벤트 (domain.md 7절).
 * ip_address, user_agent 는 오직 문자열로만 취급한다 (지역 변환·분석 없음).
 */
@Getter
@AllArgsConstructor
public class LoginEvent implements Timestamped {
    private final EpochMillis timestamp;
    private final LoginEventType type;
    private final String ipAddress;
    private final String userAgent;
}
