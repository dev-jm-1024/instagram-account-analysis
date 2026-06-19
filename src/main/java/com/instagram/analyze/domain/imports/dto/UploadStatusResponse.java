package com.instagram.analyze.domain.imports.dto;

import com.instagram.analyze.domain.imports.UploadStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code POST /api/import/upload} 및 {@code GET /api/import/upload/status} 응답.
 *
 * <p>임포트 그룹과 일관되게 envelope 없이 직접 반환한다. 프론트는 status 가 COMPLETED 가 될 때까지
 * 폴링한 뒤 {@code targetPath}(또는 {@code /candidates})로 임포트를 이어간다.
 */
@Getter
@AllArgsConstructor
public class UploadStatusResponse {
    private final UploadStatus status;
    private final String fileName;        // 업로드된 원본 파일명
    private final int extractedEntries;   // 추출된 엔트리 수(ZIP, 추출 중/완료 시)
    private final String targetPath;      // 추출 디렉토리(ZIP) 또는 저장 파일 경로. import folderPath 후보
    private final String message;         // FAILED 시 사유, 그 외 null
}
