package com.instagram.analyze.domain.activity;

import com.instagram.analyze.domain.common.Timestamped;
import com.instagram.analyze.domain.common.vo.EpochMillis;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 게시물/스토리/릴스 (domain.md 4.1). timestamp = creation_timestamp.
 * media[].uri 등 미디어 경로는 다루지 않으며 메타데이터만 보관한다.
 */
@Getter
@AllArgsConstructor
public class Post implements Timestamped {
    private final EpochMillis timestamp;
    private final PostType type;
    private final String title; // 캡션
}
