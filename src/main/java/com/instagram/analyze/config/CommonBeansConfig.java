package com.instagram.analyze.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 공통 인프라 빈. (비동기 import 실행기 {@code importExecutor} 는 별도 ImportExecutorConfig 에 있음.)
 */
@Configuration
public class CommonBeansConfig {

    /** 시간 소스 — 직접 System.currentTimeMillis() 대신 주입해 일관성·테스트 결정성 확보. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
