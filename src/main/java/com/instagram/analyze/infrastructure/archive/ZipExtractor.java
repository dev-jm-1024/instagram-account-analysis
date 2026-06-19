package com.instagram.analyze.infrastructure.archive;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.IntConsumer;

/**
 * ZIP 압축 해제 (260605 업로드 흐름) — 파일시스템 IO 이므로 {@code infrastructure} 에 둔다.
 *
 * <p>전체 추출(미디어 포함, 사용자 결정 2026-06-05)이며 zip-slip(엔트리가 대상 디렉토리 밖을 가리킴)을
 * 방어한다. 대용량이라 엔트리를 스트리밍으로 디스크에 흘려 메모리를 평탄하게 유지한다.
 */
public interface ZipExtractor {

    /**
     * {@code zip} 을 {@code targetDir} 아래로 전체 추출한다.
     *
     * @param onEntry 추출 완료한 엔트리 누적 수 콜백(진행률 표시용, null 허용)
     * @return 추출한 엔트리 총 수
     * @throws IOException zip-slip 감지·IO 오류
     */
    int extract(Path zip, Path targetDir, IntConsumer onEntry) throws IOException;
}
