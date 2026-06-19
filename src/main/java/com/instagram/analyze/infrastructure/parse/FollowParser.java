package com.instagram.analyze.infrastructure.parse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import com.instagram.analyze.config.FollowMappingProperties;
import com.instagram.analyze.config.FollowMappingProperties.FollowFile;
import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.common.vo.Username;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.follow.FollowRelationType;
import com.instagram.analyze.infrastructure.text.StringNormalizer;

/**
 * 팔로우 관계 파서 (domain.md 2절). 파일명 → 루트 키·관계 타입 매핑은 외부
 * {@code instagram-schema-mapping.yaml}({@link FollowMappingProperties})에서 온다(B1b). 분할 파일
 * (followers_1/2)은 전부 합산. 각 항목: {@code string_list_data[0].value}(username, 없으면 title)
 * / {@code .timestamp}(초) → epoch ms.
 */
@Component
public class FollowParser extends AbstractJsonParser {

    private final FollowMappingProperties followMapping;

    public FollowParser(StringNormalizer normalizer, FollowMappingProperties followMapping) {
        super(normalizer);
        this.followMapping = followMapping;
    }

    public List<FollowEntry> parse(List<Path> followFiles, ParseWarnings warnings) {
        List<FollowEntry> result = new ArrayList<>();
        for (Path file : followFiles) {
            Optional<FollowFile> mapping = followMapping.match(file.getFileName().toString());
            if (mapping.isEmpty()) {
                continue;
            }
            FollowFile type = mapping.get();
            // 팔로우 파일은 작아 루트를 통째로 읽고 3형태 모두 처리: ① 배열 루트 ② rootKey 하위 배열
            // ③ 단일 객체 레코드(recently_unfollowed: label_values 1건이 배열 없이 바로 루트에 옴).
            JsonNode root = readRoot(file, warnings);
            if (root == null) {
                continue;
            }
            JsonNode array = followArray(root, type.getRootKey());
            if (array != null) {
                for (JsonNode el : array) {
                    if (el != null && el.isObject()) {
                        parseElement(el, type.getRelation(), file, warnings, result);
                    }
                }
            } else if (root.isObject() && isFollowRecord(root)) {
                parseElement(root, type.getRelation(), file, warnings, result);
            } else {
                warnings.add(ParseWarningCode.SCHEMA_MISMATCH, file.toString(),
                        "root key not found: " + type.getRootKey());
            }
        }
        return result;
    }

    /** 배열 루트 또는 rootKey 하위 배열. 빈 {@code media:[]} 등 오인 방지로 rootKey 만 신뢰(첫배열 폴백 안 함). */
    private JsonNode followArray(JsonNode root, String rootKey) {
        if (root.isArray()) {
            return root;
        }
        JsonNode byKey = root.get(rootKey);
        return (byKey != null && byKey.isArray()) ? byKey : null;
    }

    /** 배열 없이 루트가 곧 1개 레코드인지(username 추출 단서 보유). */
    private boolean isFollowRecord(JsonNode root) {
        return root.has("label_values") || root.has("string_list_data") || root.has("title");
    }

    private void parseElement(JsonNode element, FollowRelationType relationType, Path file,
                              ParseWarnings warnings, List<FollowEntry> out) {
        // 실데이터 변형: following.json 은 username 을 string_list_data[].value 가 아니라 title 에 둔다
        // (string_list_data 엔 href·timestamp 만). value 우선, 없으면 title 로 폴백한다.
        JsonNode first = firstStringListData(element);   // null 가능
        String value = first != null ? text(first.get("value")) : null;
        if (value == null) {
            value = text(element.get("title"));
        }
        if (value == null) {
            // close_friends·blocked_profiles 는 label_values 리스트형(string_list_data·title 없음).
            value = labelValuesUsername(element);
        }
        if (value == null) {
            warnings.add(first == null ? ParseWarningCode.STRING_LIST_EMPTY : ParseWarningCode.VALUE_BLANK,
                    file.toString(), null);
            return;
        }
        // 팔로우는 username 이 1차 키 — timestamp 가 없거나 불량이어도 관계는 유지한다(nullable).
        // ts 는 string_list_data 우선, 없으면 최상위 timestamp(label_values 폼).
        JsonNode timestamp = first != null ? first.get("timestamp") : element.get("timestamp");
        EpochMillis followedAt = (timestamp != null && timestamp.isNumber() && timestamp.asLong() > 0)
                ? EpochMillis.normalize(timestamp.asLong())
                : null;
        out.add(new FollowEntry(followedAt, Username.of(value), relationType));
    }
}
