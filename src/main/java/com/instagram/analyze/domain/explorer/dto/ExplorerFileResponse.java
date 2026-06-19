package com.instagram.analyze.domain.explorer.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/explorer/file?path=...} 응답: 원본 JSON + truncated 플래그.
 */
@Getter
@AllArgsConstructor
public class ExplorerFileResponse {
    private final String path;
    private final String content;
    private final boolean truncated;
}
