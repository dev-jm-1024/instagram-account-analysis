package com.instagram.analyze.api.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA 폴백 — 클라이언트 라우트(/main, /upload 등)로 직접 접근하거나 새로고침할 때
 * Spring 이 404 를 내지 않고 {@code index.html} 로 포워드해 React Router 가 렌더하게 한다.
 *
 * 매칭 규칙: 점(.)이 없는 단일 세그먼트 경로만(정적 자원 .js/.css/.png 등 제외).
 * 음수 룩어헤드로 {@code /api} 는 제외 → REST 엔드포인트는 그대로 동작.
 * {@code /} 는 매칭하지 않으므로 기본 welcome-page 핸들러가 index.html 을 서빙한다.
 */
@Controller
public class SpaForwardingController {

    @GetMapping("/{path:^(?!api$)[^.]*}")
    public String forwardSpaRoute() {
        return "forward:/index.html";
    }
}
