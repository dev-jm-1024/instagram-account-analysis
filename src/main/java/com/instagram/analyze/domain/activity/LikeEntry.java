package com.instagram.analyze.domain.activity;

import com.instagram.analyze.domain.common.Timestamped;
import com.instagram.analyze.domain.common.vo.EpochMillis;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 좋아요 단건 (domain.md 4.2). timestamp = string_list_data[0].timestamp.
 */
@Getter
@AllArgsConstructor
public class LikeEntry implements Timestamped {
    private final EpochMillis timestamp;
    private final LikeTargetType target;
    private final String href;
}
