package com.instagram.analyze.application.imports;

import java.io.InputStream;

/**
 * ZIP/JSON 업로드 → data/ 저장 → (ZIP) 비동기 전체 추출 (260605 업로드 흐름).
 *
 * <p>외부 전송 없이 브라우저 → localhost → 로컬 디스크. 저장은 스트리밍(메모리 평탄),
 * 추출은 비동기이며 프론트는 {@link #status()} 를 폴링한다. 추출 완료 후 기존
 * {@code ExportDiscoveryService}/{@code ImportService} 가 받는다.
 */
public interface UploadService {

    /**
     * 업로드 본문을 data/ 에 저장하고, ZIP 이면 비동기 추출을 시작한다.
     *
     * <p>저장(스트리밍)은 동기로 끝낸 뒤 즉시 반환한다 — ZIP 이면 {@code EXTRACTING},
     * 단일 파일이면 {@code COMPLETED}. 호출 스레드는 업로드 본문을 다 읽는 동안만 점유된다.
     *
     * @param originalFilename 업로드 원본 파일명(경로 구분자는 제거됨)
     * @param content          업로드 본문 스트림(디스크로 흘려보냄)
     */
    UploadState upload(String originalFilename, InputStream content);

    /** 현재 업로드·추출 상태(폴링 대상). */
    UploadState status();
}
