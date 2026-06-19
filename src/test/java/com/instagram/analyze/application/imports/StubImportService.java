package com.instagram.analyze.application.imports;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.store.ImportSnapshot;
import com.instagram.analyze.application.store.ImportWritePort;
import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.common.vo.Username;
import com.instagram.analyze.domain.heatmap.ActivityHeatmap;
import com.instagram.analyze.domain.imports.ImportResult;
import com.instagram.analyze.domain.imports.ImportStatus;

/**
 * 테스트 픽스처 — 파서 없이 빈 데이터로 동기 즉시 완료시키는 stub {@link ImportService}.
 * (프로덕션은 {@link RealImportService}. 이 클래스는 게이트/탐색기 단위 테스트에서 상태를 빠르게 세팅하는 용도.)
 */
public class StubImportService implements ImportService {

    private final ImportReadStore readStore;
    private final ImportWritePort writePort;

    public StubImportService(ImportReadStore readStore, ImportWritePort writePort) {
        this.readStore = readStore;
        this.writePort = writePort;
    }

    @Override
    public ImportResult importFrom(String folderPath) {
        if (folderPath == null || folderPath.isBlank()) {
            throw new IllegalArgumentException("folderPath must not be blank");
        }
        writePort.markInProgress(Path.of(folderPath));
        ImportResult completed = new ImportResult(ImportStatus.COMPLETED, null, false,
                EpochMillis.of(System.currentTimeMillis()), 0L, 0, List.of());
        writePort.replaceAll(ImportSnapshot.builder()
                .heatmap(new ActivityHeatmap(null, null))
                .importResult(completed)
                .scannedFiles(Map.of())
                .build());
        return readStore.importResult();
    }

    @Override
    public ImportResult status() {
        return readStore.importResult();
    }

    @Override
    public ImportResult resolveOwner(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username must not be blank");
        }
        AccountIdentity owner = new AccountIdentity(Username.of(username), username);
        writePort.applyOwner(owner, readStore.conversations());
        return readStore.importResult();
    }

    @Override
    public ImportResult reset() {
        writePort.reset();
        return readStore.importResult();
    }
}
