package com.instagram.analyze.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.application.imports.ExportCandidate;
import com.instagram.analyze.application.imports.ExportDiscoveryService;
import com.instagram.analyze.config.InstagramProperties;

/**
 * data/ 얕은 스캔 export 탐지 (260604_data_scan_plan_v1 §2.2).
 */
class ExportDiscoveryServiceTest {

    @TempDir
    Path dataRoot;

    private ExportDiscoveryService service(Path root) {
        InstagramProperties props = new InstagramProperties();
        props.getData().setRoot(root.toString());
        return new ExportDiscoveryService(props);
    }

    @Test
    void detectsNestedExportDir_andParsesNameMetadata() throws IOException {
        // 표준 중첩 구조: marker 가 깊이 3(connections/followers_and_following/following.json)
        write("instagram-myaccount-2026-06-03-igilBpS1/connections/followers_and_following/following.json", "{}");

        List<ExportCandidate> result = service(dataRoot).discover();

        assertEquals(1, result.size());
        ExportCandidate c = result.get(0);
        assertEquals("instagram-myaccount-2026-06-03-igilBpS1", c.name());
        assertEquals("myaccount", c.account());
        assertEquals("2026-06-03", c.exportedAt());
        assertTrue(c.path().endsWith("instagram-myaccount-2026-06-03-igilBpS1"));
    }

    @Test
    void ignoresNonExportDirsAndLooseFiles() throws IOException {
        write("not-an-export/readme.txt", "hello");
        write("empty-dir/.keep", "");
        Files.createDirectories(dataRoot.resolve("totally-empty"));

        assertTrue(service(dataRoot).discover().isEmpty());
    }

    @Test
    void sortsByExportDateDescending() throws IOException {
        write("instagram-a-2026-01-01-aaaa/following.json", "{}");          // marker 깊이 1(평면 구조)
        write("instagram-b-2026-05-09-bbbb/connections/followers_1.json", "{}");

        List<ExportCandidate> result = service(dataRoot).discover();

        assertEquals(2, result.size());
        assertEquals("2026-05-09", result.get(0).exportedAt());   // 최신 우선
        assertEquals("2026-01-01", result.get(1).exportedAt());
    }

    @Test
    void nonMatchingDirNameKeepsCandidateWithNullMetadata() throws IOException {
        write("my-export-folder/posts_1.json", "{}");

        List<ExportCandidate> result = service(dataRoot).discover();

        assertEquals(1, result.size());
        assertEquals("my-export-folder", result.get(0).name());
        assertNull(result.get(0).account());
        assertNull(result.get(0).exportedAt());
    }

    @Test
    void missingDataRoot_returnsEmptyNotError() {
        ExportDiscoveryService service = service(dataRoot.resolve("does-not-exist"));
        assertTrue(service.discover().isEmpty());
    }

    private void write(String relative, String content) throws IOException {
        Path target = dataRoot.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
