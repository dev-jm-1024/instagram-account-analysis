package com.instagram.analyze.api.imports;

import java.util.List;

import org.springframework.stereotype.Component;

import com.instagram.analyze.application.imports.ExportCandidate;
import com.instagram.analyze.application.imports.UploadState;
import com.instagram.analyze.domain.imports.ImportResult;
import com.instagram.analyze.domain.imports.dto.CandidatesResponse;
import com.instagram.analyze.domain.imports.dto.ImportStatusResponse;
import com.instagram.analyze.domain.imports.dto.UploadStatusResponse;

/**
 * {@link ImportResult}(도메인) → API DTO 변환. import 관련 DTO 생성의 유일 지점.
 */
@Component
public class ImportAssembler {

    public ImportStatusResponse toStatus(ImportResult result) {
        return new ImportStatusResponse(
                result.getStatus(),
                result.isOwnerResolved(),
                result.getDurationMillis(),
                result.getParsedItemCount(),
                result.getWarnings());
    }

    public CandidatesResponse toCandidates(String dataRoot, boolean autoImportSingle,
                                           List<ExportCandidate> candidates) {
        List<CandidatesResponse.Candidate> views = candidates.stream()
                .map(c -> new CandidatesResponse.Candidate(c.path(), c.name(), c.account(), c.exportedAt()))
                .toList();
        return new CandidatesResponse(dataRoot, autoImportSingle, views);
    }

    public UploadStatusResponse toUpload(UploadState state) {
        return new UploadStatusResponse(
                state.status(),
                state.fileName(),
                state.extractedEntries(),
                state.targetPath(),
                state.message());
    }
}
