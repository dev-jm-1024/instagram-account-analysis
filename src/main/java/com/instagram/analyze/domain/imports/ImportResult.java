package com.instagram.analyze.domain.imports;

import java.util.List;

import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.common.vo.ParseWarning;

import lombok.Getter;

/**
 * 임포트 1회 수행 결과 메타데이터 (domain.md 1절).
 * 경고 리스트는 방어복사하여 불변으로 노출한다.
 */
@Getter
public final class ImportResult {

    private final ImportStatus status;
    private final AccountIdentity owner;        // 본인 식별 결과 (자동 실패 시 null 가능)
    private final boolean ownerResolved;        // 자동/수동 본인식별 성공 여부 (1.4)
    private final EpochMillis completedAt;       // 완료 시각 (미완료 시 null)
    private final long durationMillis;           // 소요 시간
    private final int parsedItemCount;           // 파싱된 항목 수
    private final List<ParseWarning> warnings;   // 누적 경고 (G6)

    public ImportResult(ImportStatus status, AccountIdentity owner, boolean ownerResolved,
                        EpochMillis completedAt, long durationMillis, int parsedItemCount,
                        List<ParseWarning> warnings) {
        this.status = status;
        this.owner = owner;
        this.ownerResolved = ownerResolved;
        this.completedAt = completedAt;
        this.durationMillis = durationMillis;
        this.parsedItemCount = parsedItemCount;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
