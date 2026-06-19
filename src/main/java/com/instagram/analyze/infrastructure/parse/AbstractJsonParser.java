package com.instagram.analyze.infrastructure.parse;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.infrastructure.text.StringNormalizer;

/**
 * 도메인 파서 공통 베이스 — Jackson 3 스트리밍으로 "root key 하위 배열"을 한 원소씩 순회한다.
 *
 * <p>스트리밍 + 원소 단위 subtree(readTree) 하이브리드: 파일 전체를 메모리에 올리지 않아 대용량
 * (좋아요·댓글)에 안전하면서, 각 원소는 JsonNode 로 다루기 쉽다. 문자열은 {@link StringNormalizer} 통과.
 */
public abstract class AbstractJsonParser {

    protected final ObjectMapper mapper = JsonMapper.builder().build();
    protected final StringNormalizer normalizer;

    protected AbstractJsonParser(StringNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    /** file 의 rootKeys 중 하나에 해당하는 배열의 각 원소를 onElement 로 흘린다. 루트 자체가 배열이면 그대로. */
    protected void streamArray(Path file, Set<String> rootKeys, ParseWarnings warnings, Consumer<JsonNode> onElement) {
        try (JsonParser parser = mapper.createParser(file)) {
            JsonToken first = parser.nextToken();
            if (first == JsonToken.START_ARRAY) {
                consumeArray(parser, onElement);
                return;
            }
            if (first != JsonToken.START_OBJECT) {
                warnings.add(ParseWarningCode.SCHEMA_MISMATCH, file.toString(), "root is not object/array");
                return;
            }
            boolean consumed = false;
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String name = parser.currentName();
                parser.nextToken(); // 값 토큰으로 이동
                if (!consumed && rootKeys.contains(name) && parser.currentToken() == JsonToken.START_ARRAY) {
                    consumeArray(parser, onElement);
                    consumed = true;
                } else {
                    parser.skipChildren(); // 매칭 안 되는 값은 통째로 스킵(깊이 1 유지)
                }
            }
            if (!consumed) {
                warnings.add(ParseWarningCode.SCHEMA_MISMATCH, file.toString(), "root key not found: " + rootKeys);
            }
        } catch (RuntimeException e) {   // Jackson 3 예외는 unchecked
            warnings.add(ParseWarningCode.JSON_ERROR, file.toString(), e.getMessage());
        }
    }

    /**
     * root key 를 모를 때: 최상위에서 처음 만나는 배열(루트 자체가 배열이거나 첫 배열 프로퍼티)을 순회한다.
     * activity/login/search/misclog 처럼 파일당 의미 있는 배열이 하나인 경우에 쓴다.
     */
    protected void streamFirstArray(Path file, ParseWarnings warnings, Consumer<JsonNode> onElement) {
        try (JsonParser parser = mapper.createParser(file)) {
            JsonToken first = parser.nextToken();
            if (first == JsonToken.START_ARRAY) {
                consumeArray(parser, onElement);
                return;
            }
            if (first != JsonToken.START_OBJECT) {
                warnings.add(ParseWarningCode.SCHEMA_MISMATCH, file.toString(), "root is not object/array");
                return;
            }
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                parser.nextToken();
                if (parser.currentToken() == JsonToken.START_ARRAY) {
                    consumeArray(parser, onElement);
                    return;
                }
                parser.skipChildren();
            }
            warnings.add(ParseWarningCode.SCHEMA_MISMATCH, file.toString(), "no array found");
        } catch (RuntimeException e) {
            warnings.add(ParseWarningCode.JSON_ERROR, file.toString(), e.getMessage());
        }
    }

    /**
     * 파일 루트를 통째로 JsonNode 트리로 읽는다(배열·객체 무관). 범용 뷰어(MiscLog catch-all)처럼
     * 스키마를 모르고 배열/단일객체 양쪽을 다뤄야 할 때 쓴다. 비거나 파싱 실패 시 null + 경고.
     */
    protected JsonNode readRoot(Path file, ParseWarnings warnings) {
        try (JsonParser parser = mapper.createParser(file)) {
            if (parser.nextToken() == null) {
                warnings.add(ParseWarningCode.FILE_EMPTY, file.toString(), null);
                return null;
            }
            return parser.readValueAsTree();
        } catch (RuntimeException e) {
            warnings.add(ParseWarningCode.JSON_ERROR, file.toString(), e.getMessage());
            return null;
        }
    }

    /** number 노드면 long, 아니면 null. */
    protected Long numberOrNull(JsonNode node) {
        return (node != null && node.isNumber()) ? node.asLong() : null;
    }

    /** string_list_data[0] 노드(없으면 null). */
    protected JsonNode firstStringListData(JsonNode element) {
        JsonNode list = element.get("string_list_data");
        if (list != null && list.isArray() && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }

    /**
     * label_values 리스트형(좋아요·저장·피드 게시물)에서 주어진 label 의 링크를 꺼낸다.
     * 구조: {@code label_values:[{label, value, href}]}. "URL" 라벨은 mojibake 되지 않아 그대로 매칭.
     * href 우선, 없으면 value. label_values 자체가 없거나 매칭 실패 시 null.
     */
    protected String labelValue(JsonNode element, String label) {
        JsonNode list = element.get("label_values");
        if (list == null || !list.isArray()) {
            return null;
        }
        for (JsonNode entry : list) {
            JsonNode labelNode = entry.get("label");
            if (labelNode != null && labelNode.isString() && labelNode.asString().equals(label)) {
                String href = text(entry.get("href"));
                return href != null ? href : text(entry.get("value"));
            }
        }
        return null;
    }

    /** label_values 의 username 류 label(정규화·소문자 기준). 새 로케일 export 만나면 여기 추가. */
    private static final Set<String> USERNAME_LABELS = Set.of("사용자 이름", "username", "user name");

    /**
     * label_values 리스트형 팔로우 파일(close_friends·blocked_profiles)에서 username 추출.
     * 구조: {@code label_values:[{label:"URL",value:""}, {label:"이름",value:displayName},
     * {label:"사용자 이름",value:username}]}. URL value 가 비어 못 쓰므로, 정규화한 label 이 username
     * 류({@link #USERNAME_LABELS})면 그 value 를, 아니면 마지막 비어있지 않은 value(이름→사용자이름 순서상
     * username)를 쓴다. label_values 가 없으면 null.
     */
    protected String labelValuesUsername(JsonNode element) {
        JsonNode list = element.get("label_values");
        if (list == null || !list.isArray()) {
            return null;
        }
        String last = null;
        for (JsonNode entry : list) {
            String value = text(entry.get("value"));
            if (value == null) {
                continue;
            }
            String label = text(entry.get("label"));
            if (label != null && USERNAME_LABELS.contains(label.toLowerCase(Locale.ROOT))) {
                return value;
            }
            last = value;
        }
        return last;
    }

    /** string_map_data[mapKey].value (정규화). */
    protected String mapValue(JsonNode element, String mapKey) {
        JsonNode smd = element.get("string_map_data");
        if (smd == null) {
            return null;
        }
        JsonNode entry = smd.get(mapKey);
        return entry == null ? null : text(entry.get("value"));
    }

    /** string_map_data[mapKey].timestamp. */
    protected Long mapTimestamp(JsonNode element, String mapKey) {
        JsonNode smd = element.get("string_map_data");
        if (smd == null) {
            return null;
        }
        JsonNode entry = smd.get(mapKey);
        return entry == null ? null : numberOrNull(entry.get("timestamp"));
    }

    private void consumeArray(JsonParser parser, Consumer<JsonNode> onElement) {
        // 원소 단위 읽기는 readValueAsTree() 사용 — mapper.readTree(parser) 는 스트림 전체를 읽고
        // trailing-token 검증을 해 배열 중간에서 실패한다(Jackson 3).
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            JsonNode element = parser.readValueAsTree();
            onElement.accept(element);
        }
    }

    /** 문자열 노드 → 정규화된 값. 비-문자열/blank 면 null. */
    protected String text(JsonNode node) {
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.asString();
        if (value.isBlank()) {
            return null;
        }
        return normalizer.normalize(value);
    }
}
