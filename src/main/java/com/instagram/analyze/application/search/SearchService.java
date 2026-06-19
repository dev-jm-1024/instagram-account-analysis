package com.instagram.analyze.application.search;

import java.util.List;

import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.search.vo.SearchFrequency;

/**
 * 검색 기록(6) 조회 서비스 (interface_plan §4.6).
 */
public interface SearchService {

    /**
     * 검색어 빈도(내림차순). {@code GET /api/searches}
     *
     * <p>소스 파일이 없으면 {@code Sourced.absent(빈 리스트)} → Assembler 가
     * {@code SEARCH_HISTORY_NOT_FOUND}(200, G4) code 를 부여한다.
     */
    Sourced<List<SearchFrequency>> frequencies();
}
