package com.instagram.analyze.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 설정. CORS 는 속성-게이트 — {@code instagram.web.cors.allowed-origins} 가 비어있으면 비활성(기본).
 *
 * <p>SPA forwarding(비-API 경로 → index.html)은 프론트 번들(static/index.html) 확정 후 추가한다(이번 범위 외).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final InstagramProperties properties;

    public WebConfig(InstagramProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverterFactory(new IgnoreCaseEnumConverterFactory());   // ?type=post == POST
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = properties.getWeb().getCors().getAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            return;   // 기본: CORS 비활성(운영=번들 동일 origin)
        }
        registry.addMapping("/api/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "POST");
    }
}
