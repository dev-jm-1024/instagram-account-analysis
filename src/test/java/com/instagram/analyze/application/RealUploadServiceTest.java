package com.instagram.analyze.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.application.imports.DataRetentionService;
import com.instagram.analyze.application.imports.ExportDiscoveryService;
import com.instagram.analyze.application.imports.RealUploadService;
import com.instagram.analyze.application.imports.UploadState;
import com.instagram.analyze.config.InstagramProperties;
import com.instagram.analyze.domain.imports.UploadStatus;
import com.instagram.analyze.infrastructure.archive.DefaultZipExtractor;

/**
 * 업로드 → data/ 저장 → ZIP 비동기 추출. 동기 실행기로 결정적 검증.
 */
class RealUploadServiceTest {

    @TempDir
    Path dataRoot;

    private RealUploadService service() {
        return service(new InstagramProperties());
    }

    private RealUploadService service(InstagramProperties props) {
        props.getData().setRoot(dataRoot.toString());
        Executor sync = Runnable::run;   // 추출 즉시 실행 → 반환 시 이미 COMPLETED
        DataRetentionService retention =
                new DataRetentionService(props, new ExportDiscoveryService(props));
        return new RealUploadService(props, new DefaultZipExtractor(), retention, sync);
    }

    @Test
    void uploadZip_savesAndExtractsIntoBasenameDir() throws IOException {
        byte[] zip = zipBytes();

        UploadState state = service().upload("instagram-me-2026-06-03-abcd.zip", new ByteArrayInputStream(zip));

        assertEquals(UploadStatus.COMPLETED, state.status());
        assertTrue(Files.exists(dataRoot.resolve("instagram-me-2026-06-03-abcd.zip")));   // 원본 ZIP 보관
        assertTrue(Files.exists(dataRoot.resolve("instagram-me-2026-06-03-abcd/following.json"))); // 추출본
        assertTrue(state.targetPath().endsWith("instagram-me-2026-06-03-abcd"));
        assertEquals(1, state.extractedEntries());
    }

    @Test
    void reupload_clearsStaleOrphansFromPreviousExtract() throws IOException {
        RealUploadService service = service();
        String name = "instagram-me-2026-06-03-abcd.zip";

        // A: following.json + followers_2.json
        service.upload(name, new ByteArrayInputStream(zipWith("following.json", "followers_2.json")));
        assertTrue(Files.exists(dataRoot.resolve("instagram-me-2026-06-03-abcd/followers_2.json")));

        // B: 같은 basename, following.json 만 → 이전 followers_2.json 은 orphan 으로 남으면 안 됨
        UploadState state = service.upload(name, new ByteArrayInputStream(zipWith("following.json")));

        assertEquals(UploadStatus.COMPLETED, state.status());
        assertTrue(Files.exists(dataRoot.resolve("instagram-me-2026-06-03-abcd/following.json")));
        assertFalse(Files.exists(dataRoot.resolve("instagram-me-2026-06-03-abcd/followers_2.json")));
        assertEquals(1, state.extractedEntries());
    }

    @Test
    void retention_prunesOldestExportBeyondKeep() throws IOException {
        InstagramProperties props = new InstagramProperties();
        props.getData().setKeep(2);
        RealUploadService svc = service(props);

        svc.upload("instagram-me-2026-06-01-aaaa.zip", new ByteArrayInputStream(zipBytes()));
        svc.upload("instagram-me-2026-06-02-bbbb.zip", new ByteArrayInputStream(zipBytes()));
        svc.upload("instagram-me-2026-06-03-cccc.zip", new ByteArrayInputStream(zipBytes()));

        // keep=2 → 최신 2개(06-02·06-03) 유지, 가장 오래된 06-01 은 dir+zip 삭제
        assertFalse(Files.exists(dataRoot.resolve("instagram-me-2026-06-01-aaaa")));
        assertFalse(Files.exists(dataRoot.resolve("instagram-me-2026-06-01-aaaa.zip")));
        assertTrue(Files.exists(dataRoot.resolve("instagram-me-2026-06-02-bbbb")));
        assertTrue(Files.exists(dataRoot.resolve("instagram-me-2026-06-03-cccc")));
    }

    @Test
    void uploadNonZip_savesFileAndCompletes() {
        UploadState state = service().upload("following.json", new ByteArrayInputStream("{}".getBytes()));

        assertEquals(UploadStatus.COMPLETED, state.status());
        assertTrue(Files.exists(dataRoot.resolve("following.json")));
        assertTrue(state.targetPath().endsWith("following.json"));
    }

    @Test
    void blankFileName_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> service().upload("   ", new ByteArrayInputStream(new byte[0])));
    }

    @Test
    void pathInFileName_isStrippedToBasename() throws IOException {
        UploadState state = service().upload("../../evil/following.json",
                new ByteArrayInputStream("{}".getBytes()));

        assertEquals(UploadStatus.COMPLETED, state.status());
        assertTrue(Files.exists(dataRoot.resolve("following.json")));   // data/ 안에만 저장
    }

    private byte[] zipBytes() throws IOException {
        return zipWith("following.json");
    }

    private byte[] zipWith(String... entryNames) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (String name : entryNames) {
                zos.putNextEntry(new ZipEntry(name));
                zos.write("{}".getBytes());
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
