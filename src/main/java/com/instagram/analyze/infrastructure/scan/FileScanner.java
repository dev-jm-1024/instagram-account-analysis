package com.instagram.analyze.infrastructure.scan;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.instagram.analyze.domain.common.DomainType;

/**
 * 파일 스캐너 (domain.md 1.1) — 파일시스템 IO 이므로 {@code infrastructure} 에 둔다.
 *
 * <p>루트를 재귀 탐색하여 glob 패턴(YAML 매핑)에 맞는 파일을 도메인별로 수집한다.
 * 미디어 확장자({@code MediaType}) 와 0바이트 파일은 제외한다. 매핑에 없는 파일은
 * {@code DomainType.UNKNOWN} 으로 분류한다.
 */
public interface FileScanner {

    /** 루트 디렉토리를 스캔하여 도메인별 파일 목록을 반환한다. */
    Map<DomainType, List<Path>> scan(Path root);
}
