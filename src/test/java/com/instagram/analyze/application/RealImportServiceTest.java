package com.instagram.analyze.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.application.imports.ImportPipeline;
import com.instagram.analyze.application.imports.ImportReimportRequiredException;
import com.instagram.analyze.application.imports.ImportValidationException;
import com.instagram.analyze.application.imports.RealImportService;
import com.instagram.analyze.application.imports.UploadInProgressException;
import com.instagram.analyze.application.imports.UploadService;
import com.instagram.analyze.application.imports.UploadState;
import com.instagram.analyze.application.store.InMemoryImportStore;
import com.instagram.analyze.config.FollowMappingProperties;
import com.instagram.analyze.config.ScanMappingProperties;
import com.instagram.analyze.domain.imports.ImportStatus;
import com.instagram.analyze.domain.imports.UploadStatus;
import com.instagram.analyze.infrastructure.identity.DefaultAccountIdentityResolver;
import com.instagram.analyze.infrastructure.parse.ActivityParser;
import com.instagram.analyze.infrastructure.parse.FollowParser;
import com.instagram.analyze.infrastructure.parse.LoginParser;
import com.instagram.analyze.infrastructure.parse.MessageParser;
import com.instagram.analyze.infrastructure.parse.MiscLogParser;
import com.instagram.analyze.infrastructure.parse.SearchParser;
import com.instagram.analyze.infrastructure.scan.DefaultFileScanner;
import com.instagram.analyze.infrastructure.scan.ImportValidator;
import com.instagram.analyze.infrastructure.text.DefaultStringNormalizer;

class RealImportServiceTest {

    @TempDir
    Path exportDir;

    private InMemoryImportStore store;
    private MessageParser messageParser;
    private FakeUploadService uploadService;
    private RealImportService service;

    @BeforeEach
    void setUp() {
        DefaultStringNormalizer norm = new DefaultStringNormalizer();
        messageParser = new MessageParser(norm);
        ImportPipeline pipeline = new ImportPipeline(new ImportValidator(),
                new DefaultFileScanner(ScanMappingProperties.fromClasspathYaml()),
                new DefaultAccountIdentityResolver(norm),
                new FollowParser(norm, FollowMappingProperties.fromClasspathYaml()), messageParser,
                new ActivityParser(norm), new LoginParser(norm), new SearchParser(norm), new MiscLogParser(norm));
        store = new InMemoryImportStore();
        uploadService = new FakeUploadService();   // 기본 IDLE
        // 동기 실행기 → importFrom 반환 시 이미 완료
        service = new RealImportService(pipeline, store, store, messageParser, uploadService, Runnable::run,
                java.time.Clock.systemUTC());
    }

    /** 업로드 상태만 주입하기 위한 테스트 더블 (A2 가드 검증). */
    private static class FakeUploadService implements UploadService {
        private UploadState state = UploadState.idle();

        void setStatus(UploadStatus status) {
            this.state = new UploadState(status, "x.zip", 0, null, null);
        }

        @Override
        public UploadState upload(String originalFilename, java.io.InputStream content) {
            return state;
        }

        @Override
        public UploadState status() {
            return state;
        }
    }

    private void write(String relative, String json) throws IOException {
        Path target = exportDir.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, json);
    }

    @Test
    void importFrom_validExport_transitionsToCompleted() throws IOException {
        write("following.json", "{\"relationships_following\":[{\"string_list_data\":[{\"value\":\"x\",\"timestamp\":1700000000}]}]}");
        service.importFrom(exportDir.toString());
        assertEquals(ImportStatus.COMPLETED, store.importResult().getStatus());
        assertEquals(1, store.followEntries().size());
    }

    @Test
    void importFrom_whileExtracting_rejectedWithoutScanning() throws IOException {
        write("following.json", "{\"relationships_following\":[]}");
        uploadService.setStatus(UploadStatus.EXTRACTING);   // 업로드 추출 진행 중

        assertThrows(UploadInProgressException.class, () -> service.importFrom(exportDir.toString()));
        assertEquals(ImportStatus.IDLE, store.importResult().getStatus());   // 스캔/IN_PROGRESS 진입 안 함
    }

    @Test
    void importFrom_nonExistentPath_throwsValidationBeforeInProgress() {
        ImportValidationException ex = assertThrows(ImportValidationException.class,
                () -> service.importFrom(exportDir.resolve("nope").toString()));
        assertEquals(ImportValidationException.Reason.PATH_NOT_FOUND, ex.getReason());
        assertEquals(ImportStatus.IDLE, store.importResult().getStatus());   // IN_PROGRESS 진입 안 함
    }

    @Test
    void resolveOwner_reparsesMessages_andFillsOwnerDependentStats() throws IOException {
        // personal_information 없음 + 단일 방 → import 시 owner 미해결
        write("following.json", "{\"relationships_following\":[]}");
        write("messages/inbox/alice_1/message_1.json",
                "{\"participants\":[{\"name\":\"Me\"},{\"name\":\"Alice\"}],"
                        + "\"messages\":[{\"sender_name\":\"Me\",\"timestamp_ms\":1700000060000}]}");
        service.importFrom(exportDir.toString());
        assertFalse(store.isOwnerResolved());
        assertEquals(0, store.conversations().get(0).getSentCount());

        service.resolveOwner("Me");
        assertTrue(store.isOwnerResolved());
        assertEquals(1, store.conversations().get(0).getSentCount());   // 재파싱으로 owner-의존 채워짐
    }

    @Test
    void resolveOwner_whenSourceGone_throwsReimportRequired() throws IOException {
        write("following.json", "{\"relationships_following\":[]}");
        Path msg = exportDir.resolve("messages/inbox/alice_1/message_1.json");
        Files.createDirectories(msg.getParent());
        Files.writeString(msg, "{\"participants\":[{\"name\":\"Me\"},{\"name\":\"Alice\"}],\"messages\":[]}");
        service.importFrom(exportDir.toString());

        Files.delete(msg);   // 원본 폴더/파일 사라짐
        assertThrows(ImportReimportRequiredException.class, () -> service.resolveOwner("Me"));
    }
}
