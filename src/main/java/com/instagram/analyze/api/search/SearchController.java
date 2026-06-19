package com.instagram.analyze.api.search;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.api.common.ApiResultCode;
import com.instagram.analyze.application.search.SearchService;
import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.search.vo.SearchFrequency;
import com.instagram.analyze.domain.search.dto.SearchResponse;

/** GET /api/searches (부록 A). 소스 없으면 200 + SEARCH_HISTORY_NOT_FOUND (G4). */
@RestController
@RequestMapping("/api/searches")
public class SearchController {

    private final SearchService searchService;
    private final SearchAssembler assembler;

    public SearchController(SearchService searchService, SearchAssembler assembler) {
        this.searchService = searchService;
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<SearchResponse> searches() {
        Sourced<List<SearchFrequency>> result = searchService.frequencies();
        SearchResponse body = assembler.toResponse(result.getValue());
        return result.isSourceExists()
                ? ApiResponse.ok(body)
                : ApiResponse.of(ApiResultCode.SEARCH_HISTORY_NOT_FOUND, body);
    }
}
