package com.instagram.analyze.application.login;

import java.util.List;

import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.login.LoginEvent;

/**
 * 로그인 기록(7) 조회 서비스 (interface_plan §4.7). ip/user_agent 는 문자열 그대로(지역 변환 없음).
 */
public interface LoginService {

    /**
     * 로그인/로그아웃 이벤트(timestamp 내림차순, 최신순). {@code GET /api/logins}
     *
     * <p>소스 파일이 없으면 {@code Sourced.absent(빈 리스트)} → Assembler 가
     * {@code LOGIN_HISTORY_NOT_FOUND}(200, G4) code 를 부여한다.
     */
    Sourced<List<LoginEvent>> timeline();
}
