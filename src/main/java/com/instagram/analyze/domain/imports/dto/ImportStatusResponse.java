package com.instagram.analyze.domain.imports.dto;

import java.util.List;

import com.instagram.analyze.domain.common.vo.ParseWarning;
import com.instagram.analyze.domain.imports.ImportStatus;

import lombok.Getter;

/**
 * {@code GET /api/import/status} 응답: 상태값 + ownerResolved + 진행 메타 + 누적 경고.
 */
@Getter
public class ImportStatusResponse {

    private final ImportStatus status;
    private final boolean ownerResolved;
    private final long durationMillis;
    private final int parsedItemCount;
    private final List<ParseWarning> warnings;

    public ImportStatusResponse(ImportStatus status, boolean ownerResolved, long durationMillis,
                                int parsedItemCount, List<ParseWarning> warnings) {
        this.status = status;
        this.ownerResolved = ownerResolved;
        this.durationMillis = durationMillis;
        this.parsedItemCount = parsedItemCount;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
