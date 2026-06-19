package com.instagram.analyze.domain.search;

import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.search.vo.Keyword;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 검색 기록 단건 (domain.md 6절).
 *
 * <p>keyword 가 1차 키이고 timestamp 는 부차적이다(빈도/워드클라우드에는 timestamp 가 불필요).
 * timestamp 불량이어도 검색어는 유지하므로 <b>timestamp 는 nullable</b> 이며 {@code Timestamped} 를
 * 구현하지 않는다(활동기간 산출에서 제외).
 */
@Getter
@AllArgsConstructor
public class SearchEntry {
    private final EpochMillis timestamp;   // nullable
    private final Keyword keyword;
}
