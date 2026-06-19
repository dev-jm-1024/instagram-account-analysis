package com.instagram.analyze.infrastructure.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.instagram.analyze.config.ScanMappingProperties;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.common.MediaType;

/**
 * {@link FileScanner} 구현 (domain.md 1.1). 루트를 재귀 탐색해 JSON 을 도메인별로 분류한다.
 *
 * <p>미디어 확장자·0바이트·비-JSON 은 제외. 파일→도메인 분류 규칙은 코드 상수가 아니라
 * 외부 {@code instagram-schema-mapping.yaml}({@link ScanMappingProperties})에서 온다 — 스키마 변화에
 * 재컴파일 없이 대응(domain.md 부록 B). 검색이 logged_information/ 보다 먼저 claim 하도록 규칙 순서로 보장.
 */
@Component
public class DefaultFileScanner implements FileScanner {

    private final ScanMappingProperties mapping;

    public DefaultFileScanner(ScanMappingProperties mapping) {
        this.mapping = mapping;
    }

    @Override
    public Map<DomainType, List<Path>> scan(Path root) {
        Map<DomainType, List<Path>> collected = new EnumMap<>(DomainType.class);
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String name = p.getFileName().toString();
                String lower = name.toLowerCase(Locale.ROOT);
                if (MediaType.isMedia(name) || !lower.endsWith(".json")) {
                    return;
                }
                try {
                    if (Files.size(p) == 0) {
                        return;   // PARSE_FILE_EMPTY → 스킵
                    }
                } catch (IOException e) {
                    return;
                }
                String fullLower = p.toString().toLowerCase(Locale.ROOT).replace('\\', '/');
                collected.computeIfAbsent(mapping.classify(name, fullLower), k -> new ArrayList<>()).add(p);
            });
        } catch (IOException e) {
            // 루트 자체를 못 읽으면 빈 결과
        }
        Map<DomainType, List<Path>> immutable = new EnumMap<>(DomainType.class);
        collected.forEach((domain, paths) -> immutable.put(domain, List.copyOf(paths)));
        return immutable;
    }
}
