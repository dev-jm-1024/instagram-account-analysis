package com.instagram.analyze.domain.explorer.dto;

import com.instagram.analyze.domain.explorer.ExplorerNode;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/explorer/tree} 응답: 임포트 루트 기준 디렉토리 트리.
 */
@Getter
@AllArgsConstructor
public class ExplorerTreeResponse {
    private final ExplorerNode root;
}
