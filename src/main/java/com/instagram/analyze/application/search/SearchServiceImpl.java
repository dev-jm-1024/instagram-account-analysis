package com.instagram.analyze.application.search;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.search.SearchEntry;
import com.instagram.analyze.domain.search.vo.Keyword;
import com.instagram.analyze.domain.search.vo.SearchFrequency;

/**
 * {@link SearchService} 구현. 검색어 빈도 내림차순. 소스 없으면 {@code Sourced.absent}.
 */
@Service
public class SearchServiceImpl implements SearchService {

    private final ImportReadStore store;
    private final ImportGuard guard;

    public SearchServiceImpl(ImportReadStore store, ImportGuard guard) {
        this.store = store;
        this.guard = guard;
    }

    @Override
    public Sourced<List<SearchFrequency>> frequencies() {
        guard.requireCompleted();

        Map<Keyword, Long> counts = store.searchEntries().stream()
                .collect(Collectors.groupingBy(SearchEntry::getKeyword, Collectors.counting()));
        List<SearchFrequency> freqs = counts.entrySet().stream()
                .map(e -> new SearchFrequency(e.getKey(), e.getValue()))
                .sorted(Comparator.comparingLong(SearchFrequency::getCount).reversed())
                .toList();

        return store.sourceExists(DomainType.SEARCH)
                ? Sourced.present(freqs)
                : Sourced.absent(List.of());   // SEARCH_HISTORY_NOT_FOUND(200, G4)
    }
}
