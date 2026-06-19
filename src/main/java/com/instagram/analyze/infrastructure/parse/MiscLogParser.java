package com.instagram.analyze.infrastructure.parse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.log.LogFile;
import com.instagram.analyze.domain.log.LogRecord;
import com.instagram.analyze.infrastructure.text.StringNormalizer;

/**
 * 각종 로그(8) 파서 — 고정 스키마 없는 범용 키-값 (domain.md 8). 전용 도메인이 claim 하지 않은
 * <b>모든</b> 파일을 받는 catch-all 뷰어다(스캐너가 MISC_LOG 로 분류). 파일별로 그룹화해 LogFile 로 반환.
 *
 * <p>스키마를 모르므로 루트를 통째로 읽어(배열 / 배열-프로퍼티-가진-객체 / 단일 객체) 각 원소를 평면
 * Map&lt;String,String&gt; 으로 변환한다: string_map_data + label_values + 최상위 스칼라. 키 이름에
 * time/date 가 들어간 필드(및 최상위 timestamp)는 LogRecord.timestamp 로 인식한다.
 */
@Component
public class MiscLogParser extends AbstractJsonParser {

    public MiscLogParser(StringNormalizer normalizer) {
        super(normalizer);
    }

    public List<LogFile> parse(List<Path> files, ParseWarnings warnings) {
        List<LogFile> out = new ArrayList<>();
        for (Path file : files) {
            List<LogRecord> records = new ArrayList<>();
            JsonNode root = readRoot(file, warnings);
            if (root != null) {
                JsonNode array = root.isArray() ? root : firstArrayProperty(root);
                if (array != null) {
                    for (JsonNode el : array) {
                        if (el != null && el.isObject()) {
                            records.add(toRecord(el));
                        }
                    }
                } else if (root.isObject()) {
                    records.add(toRecord(root));   // 배열 없는 단일 객체(설정 등) = 1레코드
                }
            }
            out.add(new LogFile(file.getFileName().toString(), records));
        }
        return out;
    }

    /** 객체에서 처음 만나는 배열 프로퍼티(없으면 null). 루트키를 모르는 맵형 파일 대응. */
    private JsonNode firstArrayProperty(JsonNode object) {
        for (Map.Entry<String, JsonNode> entry : object.properties()) {
            if (entry.getValue().isArray()) {
                return entry.getValue();
            }
        }
        return null;
    }

    private LogRecord toRecord(JsonNode element) {
        Map<String, String> fields = new LinkedHashMap<>();
        Long timestamp = null;

        // label_values 리스트형(좋아요·조회·광고 등): label → value(없으면 href) 평면화
        JsonNode lv = element.get("label_values");
        if (lv != null && lv.isArray()) {
            for (JsonNode entry : lv) {
                String label = text(entry.get("label"));
                if (label == null) {
                    continue;
                }
                String value = text(entry.get("value"));
                if (value == null) {
                    value = text(entry.get("href"));
                }
                if (value != null) {
                    fields.put(label, value);
                }
            }
        }

        JsonNode smd = element.get("string_map_data");
        if (smd != null && smd.isObject()) {
            for (Map.Entry<String, JsonNode> entry : smd.properties()) {
                String key = normalizer.normalize(entry.getKey());   // 키도 mojibake 복구(필드명이 한글이면 깨짐 방지)
                String value = text(entry.getValue().get("value"));
                if (value != null) {
                    fields.put(key, value);
                }
                if (isTimeKey(key)) {
                    Long ts = numberOrNull(entry.getValue().get("timestamp"));
                    if (ts != null) {
                        timestamp = ts;
                    }
                }
            }
        }

        for (Map.Entry<String, JsonNode> entry : element.properties()) {
            JsonNode value = entry.getValue();
            if (!value.isValueNode() || value.isNull()) {
                continue;
            }
            String key = normalizer.normalize(entry.getKey());
            String text = value.isString() ? normalizer.normalize(value.asString()) : value.asString();
            if (text != null && !text.isBlank()) {
                fields.put(key, text);
            }
            if (isTimeKey(key)) {
                Long ts = numberOrNull(value);
                if (ts != null) {
                    timestamp = ts;
                }
            }
        }

        // media[] 중첩(보관 게시물·프로필 사진·기타 콘텐츠 등): 캡션·시각·미디어 경로가 전부 media 안에 있어
        // 위 3패스로는 빈 행이 된다. 첫 미디어의 uri(미디어 경로)·title(캡션)·creation_timestamp 를 끌어올린다.
        JsonNode media = element.get("media");
        if (media != null && media.isArray() && !media.isEmpty()) {
            JsonNode first = media.get(0);
            if (first != null && first.isObject()) {
                String uri = text(first.get("uri"));
                if (uri != null && !fields.containsKey("uri")) {
                    fields.put("uri", uri);
                }
                String title = text(first.get("title"));
                if (title != null && !title.isBlank() && !fields.containsKey("title")) {
                    fields.put("title", title);
                }
                if (timestamp == null) {
                    Long ts = numberOrNull(first.get("creation_timestamp"));
                    if (ts != null) {
                        timestamp = ts;
                    }
                }
                if (media.size() > 1) {
                    fields.put("미디어 수", String.valueOf(media.size()));
                }
            }
        }

        // string_list_data[] (팔로우 해시태그 등): 시각이 여기에만 있는 경우 보강.
        if (timestamp == null) {
            JsonNode sld = element.get("string_list_data");
            if (sld != null && sld.isArray() && !sld.isEmpty()) {
                Long ts = numberOrNull(sld.get(0).get("timestamp"));
                if (ts != null) {
                    timestamp = ts;
                }
            }
        }

        EpochMillis when = (timestamp != null && timestamp > 0) ? EpochMillis.normalize(timestamp) : null;
        return new LogRecord(fields, when);
    }

    private boolean isTimeKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("time") || k.contains("date")
                || k.contains("시각") || k.contains("시간") || k.contains("날짜") || k.contains("일시");
    }
}
