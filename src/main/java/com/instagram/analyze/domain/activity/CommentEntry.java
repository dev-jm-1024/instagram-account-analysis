package com.instagram.analyze.domain.activity;

import com.instagram.analyze.domain.common.Timestamped;
import com.instagram.analyze.domain.common.vo.EpochMillis;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 댓글 단건 (domain.md 4.3). content 는 통계 집계용으로만 사용한다.
 */
@Getter
@AllArgsConstructor
public class CommentEntry implements Timestamped {
    private final EpochMillis timestamp;
    private final CommentSource source;
    private final String content;
}
