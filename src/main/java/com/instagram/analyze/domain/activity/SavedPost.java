package com.instagram.analyze.domain.activity;

import com.instagram.analyze.domain.common.Timestamped;
import com.instagram.analyze.domain.common.vo.EpochMillis;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 저장한 게시물 단건 (domain.md 4.4). timestamp = string_list_data[0].timestamp.
 */
@Getter
@AllArgsConstructor
public class SavedPost implements Timestamped {
    private final EpochMillis timestamp;
    private final String href;
}
