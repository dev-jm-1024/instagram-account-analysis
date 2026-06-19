package com.instagram.analyze.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 번들된 {@code instagram-schema-mapping.yaml} 을 Spring 컨텍스트 없이 바인딩한다(비-Spring 테스트·독립 사용).
 * 운영에선 {@code @ConfigurationProperties} 바인딩이 쓰이며 같은 파일을 읽는다(단일 진실).
 */
final class SchemaMappingYaml {

    private SchemaMappingYaml() {
    }

    static <T> T bind(String prefix, Class<T> type) {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        ClassPathResource resource = new ClassPathResource("instagram-schema-mapping.yaml");
        try {
            List<PropertySource<?>> sources = loader.load("schema-mapping", resource);
            StandardEnvironment env = new StandardEnvironment();
            sources.forEach(env.getPropertySources()::addLast);
            return Binder.get(env).bind(prefix, type)
                    .orElseThrow(() -> new IllegalStateException(
                            prefix + " not found in instagram-schema-mapping.yaml"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
