package com.instagram.analyze.infrastructure.identity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.Username;
import com.instagram.analyze.infrastructure.text.StringNormalizer;

/**
 * {@link AccountIdentityResolver} 구현 (domain.md 1.4).
 *
 * <p>① personal_information.json 의 username/이름 필드를 best-effort 로 탐색한다.
 * ② DM participants 교차 분석은 message 파싱이 필요하므로 ④b 에서 보강(현재 ① 실패 시 empty → 수동 fallback).
 */
@Component
public class DefaultAccountIdentityResolver implements AccountIdentityResolver {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final StringNormalizer normalizer;

    public DefaultAccountIdentityResolver(StringNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    @Override
    public Optional<AccountIdentity> resolve(Map<DomainType, List<Path>> scanned) {
        Path personalInfo = scanned.getOrDefault(DomainType.IMPORT, List.of()).stream()
                .filter(p -> p.getFileName().toString().equalsIgnoreCase("personal_information.json"))
                .findFirst()
                .orElse(null);
        if (personalInfo == null) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(Files.readAllBytes(personalInfo));
            Optional<String> username = findValue(root, "username");
            if (username.isEmpty()) {
                return Optional.empty();
            }
            String displayName = findValue(root, "name").orElse(username.get());
            return Optional.of(new AccountIdentity(
                    Username.of(normalizer.normalize(username.get())),
                    normalizer.normalize(displayName)));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** 키가 keyLower 와 일치하는 첫 노드의 값(직접 텍스트 또는 중첩 "value")을 재귀 탐색. */
    private Optional<String> findValue(JsonNode node, String keyLower) {
        if (node == null) {
            return Optional.empty();
        }
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> entry : node.properties()) {
                if (entry.getKey().toLowerCase(Locale.ROOT).equals(keyLower)) {
                    JsonNode value = entry.getValue();
                    if (value.isString() && !value.asString().isBlank()) {
                        return Optional.of(value.asString());
                    }
                    JsonNode inner = value.get("value");
                    if (inner != null && inner.isString() && !inner.asString().isBlank()) {
                        return Optional.of(inner.asString());
                    }
                }
                Optional<String> found = findValue(entry.getValue(), keyLower);
                if (found.isPresent()) {
                    return found;
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                Optional<String> found = findValue(child, keyLower);
                if (found.isPresent()) {
                    return found;
                }
            }
        }
        return Optional.empty();
    }
}
