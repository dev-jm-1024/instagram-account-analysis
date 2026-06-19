package com.instagram.analyze.application.imports;

import com.instagram.analyze.domain.imports.UploadStatus;

/**
 * 업로드·추출 진행 상태 스냅샷 (불변). {@code RealUploadService} 가 volatile 로 들고 폴링에 노출한다.
 *
 * @param status           현재 단계
 * @param fileName         업로드된 원본 파일명
 * @param extractedEntries 추출된 엔트리 수(ZIP)
 * @param targetPath       추출 디렉토리(ZIP) 또는 저장 파일 경로 — import folderPath 후보
 * @param message          FAILED 사유(그 외 null)
 */
public record UploadState(UploadStatus status, String fileName, int extractedEntries,
                          String targetPath, String message) {

    public static UploadState idle() {
        return new UploadState(UploadStatus.IDLE, null, 0, null, null);
    }

    public UploadState withStatus(UploadStatus next) {
        return new UploadState(next, fileName, extractedEntries, targetPath, message);
    }

    public UploadState withExtracted(int entries) {
        return new UploadState(status, fileName, entries, targetPath, message);
    }
}
