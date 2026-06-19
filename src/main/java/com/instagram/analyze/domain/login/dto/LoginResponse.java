package com.instagram.analyze.domain.login.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/logins} 응답: 최신순 타임라인.
 */
@Getter
public class LoginResponse {

    private final List<LoginItem> timeline;

    public LoginResponse(List<LoginItem> timeline) {
        this.timeline = timeline == null ? List.of() : List.copyOf(timeline);
    }

    @Getter
    @AllArgsConstructor
    public static class LoginItem {
        private final long timestamp; // epoch ms
        private final String type;    // LOGIN / LOGOUT
        private final String ipAddress;
        private final String userAgent;
    }
}
