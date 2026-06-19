package com.instagram.analyze.domain.imports.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/import/candidates} 응답: data/ 에서 탐지된 export 후보 목록.
 *
 * <p>임포트 그룹과 일관되게 envelope({@code ApiResponse}) 없이 직접 반환한다.
 * 프론트 분기: 0개 → 수동 경로 입력, 1개 && autoImportSingle → 자동 임포트, N개 → 사용자 선택.
 */
@Getter
public class CandidatesResponse {

    private final String dataRoot;          // 탐지 루트 절대 경로(표시용)
    private final boolean autoImportSingle; // 후보 1개 자동 임포트 정책 힌트
    private final List<Candidate> candidates;

    public CandidatesResponse(String dataRoot, boolean autoImportSingle, List<Candidate> candidates) {
        this.dataRoot = dataRoot;
        this.autoImportSingle = autoImportSingle;
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }

    @Getter
    @AllArgsConstructor
    public static class Candidate {
        private final String path;       // POST /api/import 의 folderPath 로 사용
        private final String name;
        private final String account;    // nullable
        private final String exportedAt; // yyyy-MM-dd, nullable
    }
}
