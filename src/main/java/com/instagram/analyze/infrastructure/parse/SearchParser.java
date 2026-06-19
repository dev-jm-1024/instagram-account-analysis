package com.instagram.analyze.infrastructure.parse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.search.SearchEntry;
import com.instagram.analyze.domain.search.vo.Keyword;
import com.instagram.analyze.infrastructure.text.StringNormalizer;

/**
 * 검색(6) 파서 — 검색어가 1차 키, timestamp 는 부차(nullable, 불량이어도 항목 유지).
 * 루트 키가 export 버전마다 달라 best-effort 로 string_map_data/string_list_data 에서 검색어를 뽑는다.
 */
@Component
public class SearchParser extends AbstractJsonParser {

    public SearchParser(StringNormalizer normalizer) {
        super(normalizer);
    }

    public List<SearchEntry> parse(List<Path> files, ParseWarnings warnings) {
        List<SearchEntry> out = new ArrayList<>();
        for (Path file : files) {
            streamFirstArray(file, warnings, el -> addSearch(el, out, file, warnings));
        }
        return out;
    }

    private void addSearch(JsonNode el, List<SearchEntry> out, Path file, ParseWarnings w) {
        String keyword = mapValue(el, "Search");
        if (keyword == null) {
            keyword = firstMapValue(el);
        }
        if (keyword == null) {
            JsonNode first = firstStringListData(el);
            if (first != null) {
                keyword = text(first.get("value"));
            }
        }
        if (keyword == null) {
            // 실데이터: profile_searches.json 은 검색어(username)를 title 에 둠(value 없음)
            keyword = text(el.get("title"));
        }
        if (keyword == null) {
            w.add(ParseWarningCode.VALUE_BLANK, file.toString(), null);
            return;
        }
        Long ts = mapTimestamp(el, "Time");
        if (ts == null) {
            ts = numberOrNull(el.get("timestamp"));
        }
        // 검색어는 유지하되 timestamp 는 nullable (불량이어도 빈도 집계엔 무관)
        EpochMillis when = (ts != null && ts > 0) ? EpochMillis.normalize(ts) : null;
        out.add(new SearchEntry(when, Keyword.of(keyword)));
    }

    /** string_map_data 의 첫 value(검색어 키 이름이 버전마다 달라 fallback). */
    private String firstMapValue(JsonNode el) {
        JsonNode smd = el.get("string_map_data");
        if (smd == null || !smd.isObject()) {
            return null;
        }
        for (Map.Entry<String, JsonNode> entry : smd.properties()) {
            String value = text(entry.getValue().get("value"));
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
