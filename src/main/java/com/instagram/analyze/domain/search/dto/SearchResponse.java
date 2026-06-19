package com.instagram.analyze.domain.search.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/searches} 응답: 검색어 빈도 내림차순.
 */
@Getter
public class SearchResponse {

    private final List<KeywordCount> frequencies;

    public SearchResponse(List<KeywordCount> frequencies) {
        this.frequencies = frequencies == null ? List.of() : List.copyOf(frequencies);
    }

    @Getter
    @AllArgsConstructor
    public static class KeywordCount {
        private final String keyword;
        private final long count;
    }
}
