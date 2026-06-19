package com.instagram.analyze.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.instagram.analyze.domain.common.DomainType;

import lombok.Getter;
import lombok.Setter;

/**
 * 파일 → 도메인 분류 매핑 (domain.md 1.1). 외부 {@code instagram-schema-mapping.yaml} 에서 바인딩되어
 * 스키마 변화에 코드 재컴파일 없이 대응한다. application.yaml 의 {@code spring.config.import} 로 머지된다.
 *
 * <p>평가: {@link #excludePathContains} 에 걸리면 전용 도메인 규칙을 건너뛰고, 아니면 {@link #rules} 를
 * 순서대로 평가해 첫 매칭 도메인으로 분류(규칙 내 조건은 OR)한다. 어디에도 안 걸리면 {@code MISC_LOG}
 * (범용 표 뷰어, catch-all) — 전용 메뉴가 없는 파일도 '각종 로그'에서 다 보이게 한다. exclude-path 는
 * 이제 "숨김"이 아니라 "전용 분류 제외 → 범용 표로"를 뜻한다(threads/ads 가 FOLLOW/ACTIVITY 로
 * 오분류되는 것만 막고, 데이터 자체는 노출).
 */
@Getter
@Setter
@ConfigurationProperties("instagram.scan")
public class ScanMappingProperties {

    private List<String> excludePathContains = new ArrayList<>();
    private List<Rule> rules = new ArrayList<>();

    /**
     * 파일명(소문자)·전체경로(소문자, / 구분)로 도메인을 분류한다. exclude-path 면 전용 규칙을 건너뛰고,
     * 어떤 전용 규칙에도 안 걸리면 {@code MISC_LOG}(범용 표 뷰어, catch-all)로 보낸다.
     */
    public DomainType classify(String fileName, String fullPathLower) {
        String name = fileName.toLowerCase(Locale.ROOT);
        boolean excluded = false;
        for (String ex : excludePathContains) {
            if (fullPathLower.contains(ex.toLowerCase(Locale.ROOT))) {
                excluded = true;
                break;
            }
        }
        if (!excluded) {
            for (Rule rule : rules) {
                if (rule.matches(name, fullPathLower)) {
                    return rule.getDomain();
                }
            }
        }
        return DomainType.MISC_LOG;   // catch-all: 전용 도메인 없으면 범용 '각종 로그'로 노출
    }

    @Getter
    @Setter
    public static class Rule {
        private DomainType domain;
        private List<String> nameEquals = new ArrayList<>();
        private List<String> nameStartsWith = new ArrayList<>();
        private List<String> nameEndsWith = new ArrayList<>();
        private List<String> nameContainsAll = new ArrayList<>();
        private List<String> pathContains = new ArrayList<>();

        boolean matches(String name, String fullPathLower) {
            for (String s : nameEquals) {
                if (name.equals(s.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            for (String s : nameStartsWith) {
                if (name.startsWith(s.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            for (String s : nameEndsWith) {
                if (name.endsWith(s.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            if (!nameContainsAll.isEmpty() && nameContainsAll.stream()
                    .allMatch(t -> name.contains(t.toLowerCase(Locale.ROOT)))) {
                return true;
            }
            for (String s : pathContains) {
                if (fullPathLower.contains(s.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        }
    }

    /** 번들 {@code instagram-schema-mapping.yaml} 을 Spring 컨텍스트 없이 로드한다(테스트·독립 사용). */
    public static ScanMappingProperties fromClasspathYaml() {
        return SchemaMappingYaml.bind("instagram.scan", ScanMappingProperties.class);
    }
}
