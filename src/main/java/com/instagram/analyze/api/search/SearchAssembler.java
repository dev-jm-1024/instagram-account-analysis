package com.instagram.analyze.api.search;

import java.util.List;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.search.vo.SearchFrequency;
import com.instagram.analyze.domain.search.dto.SearchResponse;

@Component
public class SearchAssembler {

    public SearchResponse toResponse(List<SearchFrequency> frequencies) {
        List<SearchResponse.KeywordCount> counts = frequencies.stream()
                .map(f -> new SearchResponse.KeywordCount(f.getKeyword().getValue(), f.getCount()))
                .toList();
        return new SearchResponse(counts);
    }
}
