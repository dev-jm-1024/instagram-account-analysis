package com.instagram.analyze.application.imports;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.store.ImportSnapshot;
import com.instagram.analyze.application.store.ImportWritePort;
import com.instagram.analyze.application.support.ImportNotCompletedException;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.ParseWarning;
import com.instagram.analyze.domain.common.vo.Username;
import com.instagram.analyze.domain.imports.ImportResult;
import com.instagram.analyze.domain.imports.UploadStatus;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.infrastructure.parse.MessageParser;
import com.instagram.analyze.infrastructure.parse.ParseWarnings;

/**
 * 실제 ETL {@link ImportService} 구현 (interface_plan ④c). StubImportService 를 대체한다.
 *
 * <p>importFrom 은 논블로킹: G1 검증·스캔을 <b>동기</b>로 끝낸 뒤(실패 시 즉시 throw → 400/422)
 * markInProgress 로 전이하고 파싱은 백그라운드 실행기에서 수행 → 즉시 IN_PROGRESS 반환.
 * 완료 시 replaceAll(COMPLETED), 치명 오류 시 markFailed(FAILED).
 */
@Service
public class RealImportService implements ImportService {

    private static final Logger log = LoggerFactory.getLogger(RealImportService.class);

    private final ImportPipeline pipeline;
    private final ImportWritePort writePort;
    private final ImportReadStore readStore;
    private final MessageParser messageParser;
    private final UploadService uploadService;
    private final Executor importExecutor;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public RealImportService(ImportPipeline pipeline, ImportWritePort writePort, ImportReadStore readStore,
                             MessageParser messageParser, UploadService uploadService,
                             Executor importExecutor, Clock clock) {
        this.pipeline = pipeline;
        this.writePort = writePort;
        this.readStore = readStore;
        this.messageParser = messageParser;
        this.uploadService = uploadService;
        this.importExecutor = importExecutor;
        this.clock = clock;
    }

    @Override
    public ImportResult importFrom(String folderPath) {
        // blank 는 @NotBlank(ImportRequest) 가 컨트롤러에서 IMPORT_PATH_BLANK(400) 로 처리한다.
        // null 만 방어 — Path.of(null) 은 NPE → 500 이 되므로 G1 예외로 전환한다.
        if (folderPath == null) {
            throw new ImportValidationException(ImportValidationException.Reason.PATH_NOT_FOUND,
                    "folderPath must not be null");
        }
        // A2: 업로드/추출 진행 중이면 거부 — 절반만 풀린 디렉토리 스캔 방지.
        UploadStatus uploadStatus = uploadService.status().status();
        if (uploadStatus == UploadStatus.SAVING || uploadStatus == UploadStatus.EXTRACTING) {
            throw new UploadInProgressException();
        }
        Path root = Path.of(folderPath);
        long startedAt = clock.millis();

        // 동기: 경로·포맷 검증 + 스캔 (G1 실패는 IN_PROGRESS 진입 전에 throw)
        Map<DomainType, List<Path>> scanned = pipeline.validateAndScan(root);

        // 진행 중 재호출 방어(단일 사용자라 UI 가 막지만 안전장치)
        if (!running.compareAndSet(false, true)) {
            return readStore.importResult();
        }
        writePort.markInProgress(root);
        importExecutor.execute(() -> {
            try {
                ImportSnapshot snapshot = pipeline.parse(scanned, startedAt, clock.millis());
                writePort.replaceAll(snapshot);
            } catch (RuntimeException e) {
                // 백그라운드라 전역 핸들러가 못 잡음 → 반드시 로깅(보강 3)
                log.error("import failed", e);
                writePort.markFailed(List.of(
                        new ParseWarning(ParseWarningCode.JSON_ERROR, "import", e.getMessage())));
            } finally {
                running.set(false);
            }
        });
        return readStore.importResult();   // IN_PROGRESS (동기 실행기면 이미 COMPLETED)
    }

    @Override
    public ImportResult status() {
        return readStore.importResult();
    }

    @Override
    public ImportResult reset() {
        writePort.reset();
        running.set(false);
        return readStore.importResult();
    }

    @Override
    public ImportResult resolveOwner(String username) {
        // blank 는 @NotBlank(OwnerRequest) 가 컨트롤러에서 OWNER_INPUT_BLANK(400) 로 처리한다.
        // null 만 방어 — Username.of(null) 이 NPE → 500 이 되는 것을 차단한다.
        if (username == null) {
            throw new IllegalArgumentException("username must not be null");
        }
        if (!readStore.isCompleted()) {
            throw new ImportNotCompletedException();
        }
        AccountIdentity owner = new AccountIdentity(Username.of(username), username);

        List<Path> messageFiles = readStore.scannedFiles(DomainType.MESSAGE);
        for (Path file : messageFiles) {
            if (!Files.exists(file)) {
                throw new ImportReimportRequiredException();   // 원본 폴더 사라짐 → 재임포트 안내
            }
        }
        ParseWarnings warnings = new ParseWarnings();
        // 재집계 — 히트맵 이중가산 방지 위해 onTimestamp 는 no-op
        List<Conversation> rebuilt = messageParser.parse(messageFiles, owner, warnings, timestamp -> { });
        writePort.applyOwner(owner, rebuilt);
        return readStore.importResult();
    }
}
