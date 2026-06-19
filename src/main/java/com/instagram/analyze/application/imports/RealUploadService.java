package com.instagram.analyze.application.imports;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.instagram.analyze.config.InstagramProperties;
import com.instagram.analyze.domain.imports.UploadStatus;
import com.instagram.analyze.infrastructure.archive.ZipExtractor;

/**
 * {@link UploadService} 구현 — 멀티파트 본문을 data/ 로 스트리밍 저장 + ZIP 비동기 전체 추출.
 *
 * <p>저장은 {@code Files.copy(InputStream, Path)} 로 8KB 단위 스트리밍이라 힙이 평탄하다. ZIP 은
 * {@code data/<basename>/} 로 추출하며(엔트리 루트가 export 디렉토리라 한 단계면 충분, 탐지 깊이 4 내),
 * 원본 ZIP 은 보관한다(사용자 결정 2026-06-05). 추출은 {@code importExecutor} 에서 비동기 실행한다
 * (단일 사용자라 import 와 시점이 겹치지 않음).
 */
@Service
public class RealUploadService implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(RealUploadService.class);

    private final InstagramProperties properties;
    private final ZipExtractor zipExtractor;
    private final Executor importExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile UploadState state = UploadState.idle();

    private final DataRetentionService retentionService;

    public RealUploadService(InstagramProperties properties, ZipExtractor zipExtractor,
                             DataRetentionService retentionService, Executor importExecutor) {
        this.properties = properties;
        this.zipExtractor = zipExtractor;
        this.retentionService = retentionService;
        this.importExecutor = importExecutor;
    }

    @Override
    public UploadState upload(String originalFilename, InputStream content) {
        String fileName = sanitize(originalFilename);
        if (fileName.isBlank()) {
            throw new IllegalArgumentException("uploaded file name must not be blank");
        }
        if (content == null) {
            throw new IllegalArgumentException("uploaded content must not be null");
        }
        if (!running.compareAndSet(false, true)) {
            return state;   // 진행 중 재호출 방어(단일 사용자라 UI 가 막지만 안전장치)
        }

        Path dataRoot = Path.of(properties.getData().getRoot()).toAbsolutePath().normalize();
        Path savedFile = dataRoot.resolve(fileName);
        boolean isZip = fileName.toLowerCase(Locale.ROOT).endsWith(".zip");

        try {
            Files.createDirectories(dataRoot);
            // 동기 저장(업로드 본문 스트리밍) — 브라우저가 이 구간을 진행률로 표시
            this.state = new UploadState(UploadStatus.SAVING, fileName, 0, null, null);
            Files.copy(content, savedFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            running.set(false);
            this.state = new UploadState(UploadStatus.FAILED, fileName, 0, null, e.getMessage());
            log.error("upload save failed: {}", fileName, e);
            return state;
        }

        if (!isZip) {
            // 단일 파일(예: 단독 JSON) — 저장만으로 완료. targetPath = 저장 파일.
            running.set(false);
            this.state = new UploadState(UploadStatus.COMPLETED, fileName, 0, savedFile.toString(), null);
            return state;
        }

        // ZIP — 비동기 전체 추출
        String base = fileName.substring(0, fileName.length() - ".zip".length());
        Path targetDir = dataRoot.resolve(base);
        this.state = new UploadState(UploadStatus.EXTRACTING, fileName, 0, targetDir.toString(), null);
        importExecutor.execute(() -> extract(savedFile, targetDir, fileName));
        return state;
    }

    private void extract(Path zip, Path targetDir, String fileName) {
        try {
            // 재업로드 시 이전 추출본을 먼저 비운다 — 안 그러면 새 export 에 없는 옛 파일(orphan)이
            // 남아 패턴 스캐너가 유령 데이터로 주워간다. REPLACE_EXISTING 은 동명 파일만 덮으므로 부족하다.
            cleanTargetDir(targetDir);
            int entries = zipExtractor.extract(zip, targetDir,
                    n -> this.state = this.state.withExtracted(n));
            retentionService.prune();   // A3: 오래된 export 정리(throw 안 함). 방금 추출본은 최신이라 보존
            this.state = new UploadState(UploadStatus.COMPLETED, fileName, entries, targetDir.toString(), null);
        } catch (IOException | RuntimeException e) {
            log.error("zip extract failed: {}", fileName, e);
            this.state = new UploadState(UploadStatus.FAILED, fileName,
                    state.extractedEntries(), targetDir.toString(), e.getMessage());
        } finally {
            running.set(false);
        }
    }

    /** 추출 대상 디렉토리를 재귀 삭제한다(재업로드 stale 정리). data/ 직속 하위 가드는 {@link DataDirs}. */
    private void cleanTargetDir(Path targetDir) throws IOException {
        Path dataRoot = Path.of(properties.getData().getRoot()).toAbsolutePath().normalize();
        DataDirs.deleteWithinRoot(targetDir, dataRoot);
    }

    @Override
    public UploadState status() {
        return state;
    }

    /** 경로 구분자를 제거해 파일명만 남긴다(디렉토리 탈출 방지). */
    private String sanitize(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim().replace('\\', '/');
        int slash = trimmed.lastIndexOf('/');
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }
}
