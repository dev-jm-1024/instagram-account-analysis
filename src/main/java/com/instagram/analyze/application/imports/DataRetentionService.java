package com.instagram.analyze.application.imports;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.instagram.analyze.config.InstagramProperties;

/**
 * data/ retention (A3) — 업로드+추출 누적으로 디스크가 무한히 커지는 것을 막는다.
 *
 * <p>업로드 성공 후 호출되어, 탐지된 export 를 최신순으로 정렬하고 {@code instagram.data.keep} 개만
 * 남긴 뒤 오래된 export 디렉토리와 동명 {@code .zip} 을 삭제한다. {@code keep <= 0} 이면 무제한(정리 안 함).
 * 삭제는 절대 throw 하지 않는다 — 정리는 부가 작업이라 실패해도 업로드 결과에 영향 없다.
 */
@Service
public class DataRetentionService {

    private static final Logger log = LoggerFactory.getLogger(DataRetentionService.class);

    private final InstagramProperties properties;
    private final ExportDiscoveryService discoveryService;

    public DataRetentionService(InstagramProperties properties, ExportDiscoveryService discoveryService) {
        this.properties = properties;
        this.discoveryService = discoveryService;
    }

    /** keep 개 초과분(오래된 export)을 디렉토리+zip 째 삭제한다. */
    public void prune() {
        int keep = properties.getData().getKeep();
        if (keep <= 0) {
            return;   // 무제한
        }
        List<ExportCandidate> candidates = discoveryService.discover();   // 최신(exportedAt desc) 우선
        if (candidates.size() <= keep) {
            return;
        }
        Path dataRoot = discoveryService.dataRoot();
        for (ExportCandidate stale : candidates.subList(keep, candidates.size())) {
            Path dir = Path.of(stale.path());
            try {
                DataDirs.deleteWithinRoot(dir, dataRoot);                       // export 디렉토리
                DataDirs.deleteWithinRoot(dataRoot.resolve(stale.name() + ".zip"), dataRoot);  // 동명 zip(있으면)
                log.info("retention: pruned old export {}", stale.name());
            } catch (IOException | RuntimeException e) {
                log.warn("retention: failed to prune {}", stale.name(), e);   // 부가 작업 — 흡수
            }
        }
    }
}
