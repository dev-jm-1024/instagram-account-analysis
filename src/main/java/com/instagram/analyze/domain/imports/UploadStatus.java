package com.instagram.analyze.domain.imports;

/**
 * ZIP 업로드·압축해제 진행 상태 (260605_frontend_integration_handoff §4 업로드 흐름).
 *
 * <p>임포트(ETL) 상태({@link ImportStatus})와 별개다 — 업로드는 그 앞단(파일 저장 → 추출) 단계이며,
 * 추출 완료 후 기존 {@code /candidates} + {@code POST /api/import} 가 받는다.
 */
public enum UploadStatus {
    IDLE,
    SAVING,       // 멀티파트 본문을 data/ 로 스트리밍 저장 중(업로드 진행률은 브라우저가 표시)
    EXTRACTING,   // 저장된 ZIP 을 비동기 전체 압축해제 중
    COMPLETED,    // 저장(+ZIP이면 추출)까지 완료 → candidates 탐지 가능
    FAILED
}
