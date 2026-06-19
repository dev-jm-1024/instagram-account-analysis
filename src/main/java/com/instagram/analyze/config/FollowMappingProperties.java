package com.instagram.analyze.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.instagram.analyze.domain.follow.FollowRelationType;

import lombok.Getter;
import lombok.Setter;

/**
 * 팔로우 파일명 → 루트 키·관계 타입 매핑 (B1b, domain.md 2절). 외부 {@code instagram-schema-mapping.yaml}
 * 의 {@code instagram.follow} 에서 바인딩되어 루트 키 변화에 코드 재컴파일 없이 대응한다.
 */
@Getter
@Setter
@ConfigurationProperties("instagram.follow")
public class FollowMappingProperties {

    private List<FollowFile> files = new ArrayList<>();

    /** 파일명(대소문자 무시)에 해당하는 매핑을 찾는다. 팔로우 파일이 아니면 empty. */
    public Optional<FollowFile> match(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return files.stream().filter(f -> f.matches(name)).findFirst();
    }

    @Getter
    @Setter
    public static class FollowFile {
        private String name;        // 정확 파일명(선택)
        private String namePrefix;  // 접두사 매칭(선택, 예: followers_)
        private String rootKey;
        private FollowRelationType relation;

        boolean matches(String lowerName) {
            if (name != null && lowerName.equals(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
            return namePrefix != null && lowerName.startsWith(namePrefix.toLowerCase(Locale.ROOT));
        }
    }

    /** 번들 {@code instagram-schema-mapping.yaml} 을 Spring 컨텍스트 없이 로드한다(테스트·독립 사용). */
    public static FollowMappingProperties fromClasspathYaml() {
        return SchemaMappingYaml.bind("instagram.follow", FollowMappingProperties.class);
    }
}
