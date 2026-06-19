package com.instagram.analyze.domain.common;

/**
 * 도메인 전체 지도 (domain.md 0.2).
 * UNKNOWN 은 YAML 매핑 사전에 정의되지 않아 분류하지 못한 파일.
 */
public enum DomainType {
    IMPORT,
    FOLLOW,
    MESSAGE,
    ACTIVITY,
    HEATMAP,
    SEARCH,
    LOGIN,
    MISC_LOG,
    OVERVIEW,
    EXPLORER,
    UNKNOWN
}
