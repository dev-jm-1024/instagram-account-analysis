package com.instagram.analyze.application.imports;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 임포트 백그라운드 실행기 (단일 사용자라 단일 스레드면 충분). 테스트는 동기 실행기로 override 가능.
 */
@Configuration
public class ImportExecutorConfig {

    @Bean
    public Executor importExecutor() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "import-worker");
            thread.setDaemon(true);
            return thread;
        });
    }
}
