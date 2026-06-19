package com.instagram.analyze.domain.explorer;

import java.util.List;

import lombok.Getter;

/**
 * 데이터 탐색기 디렉토리 트리 노드 (domain.md 10절).
 * 미디어 파일은 트리에서 제외하며 최대 깊이 10단계까지 탐색한다.
 * children 은 방어복사하여 불변으로 노출한다.
 */
@Getter
public final class ExplorerNode {

    private final String name;
    private final String relativePath; // 임포트 루트 기준 상대 경로
    private final boolean directory;
    private final List<ExplorerNode> children;

    public ExplorerNode(String name, String relativePath, boolean directory,
                        List<ExplorerNode> children) {
        this.name = name;
        this.relativePath = relativePath;
        this.directory = directory;
        this.children = children == null ? List.of() : List.copyOf(children);
    }
}
