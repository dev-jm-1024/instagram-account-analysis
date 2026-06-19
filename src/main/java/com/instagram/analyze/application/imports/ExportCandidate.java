package com.instagram.analyze.application.imports;

/**
 * {@code data/} 하위에서 탐지된 Instagram export 디렉토리 후보 (260604_data_scan_plan_v1).
 *
 * <p>{@code path} 는 {@code POST /api/import} 의 {@code folderPath} 로 그대로 넘길 절대 경로다.
 * {@code account}/{@code exportedAt} 는 디렉토리명({@code instagram-{account}-{date}-{hash}})에서
 * best-effort 로 추출하며, 패턴 불일치 시 null 이다(탐지 자체에는 영향 없음).
 *
 * @param path       export 디렉토리 절대 경로 (import 입력값)
 * @param name       디렉토리명
 * @param account    디렉토리명에서 추출한 계정명(nullable)
 * @param exportedAt 디렉토리명에서 추출한 내보내기 날짜 yyyy-MM-dd(nullable)
 */
public record ExportCandidate(String path, String name, String account, String exportedAt) {
}
