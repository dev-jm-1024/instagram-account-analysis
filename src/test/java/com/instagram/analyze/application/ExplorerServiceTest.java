package com.instagram.analyze.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.application.explorer.ExplorerFileNotFoundException;
import com.instagram.analyze.application.explorer.ExplorerPathOutOfRootException;
import com.instagram.analyze.application.explorer.ExplorerServiceImpl;
import com.instagram.analyze.application.imports.StubImportService;
import com.instagram.analyze.application.store.InMemoryImportStore;
import com.instagram.analyze.application.support.DefaultImportGuard;
import com.instagram.analyze.application.support.ImportNotCompletedException;
import com.instagram.analyze.infrastructure.text.DefaultStringNormalizer;
import com.instagram.analyze.domain.explorer.ExplorerNode;
import com.instagram.analyze.domain.explorer.RawFileContent;

/**
 * Explorer 디스크 접근 검증 — 미디어 포함 / 파일 읽기 / 미디어 경로 / G5(루트외부·미존재) / 완료 게이트.
 */
class ExplorerServiceTest {

    @TempDir
    Path root;

    private StubImportService importService;
    private ExplorerServiceImpl explorerService;

    @BeforeEach
    void setUp() {
        InMemoryImportStore store = new InMemoryImportStore();
        DefaultImportGuard guard = new DefaultImportGuard(store);
        importService = new StubImportService(store, store);
        explorerService = new ExplorerServiceImpl(store, guard, new DefaultStringNormalizer(),
                new com.instagram.analyze.config.InstagramProperties());
    }

    @Test
    void notCompleted_treeThrowsGate() {
        assertThrows(ImportNotCompletedException.class, explorerService::tree);
    }

    @Test
    void tree_includesMedia_andFileReadsContent() throws IOException {
        Files.writeString(root.resolve("a.json"), "{\"k\":1}");
        Files.createDirectory(root.resolve("sub"));
        Files.writeString(root.resolve("sub").resolve("b.json"), "{}");
        Files.writeString(root.resolve("pic.jpg"), "binary");
        importService.importFrom(root.toString());

        ExplorerNode tree = explorerService.tree();
        List<String> names = tree.getChildren().stream().map(ExplorerNode::getName).toList();
        assertTrue(names.contains("a.json"));
        assertTrue(names.contains("sub"));
        assertTrue(names.contains("pic.jpg"));   // 미디어 포함(미리보기용)

        RawFileContent file = explorerService.file("a.json");
        assertEquals("{\"k\":1}", file.getContent());
        assertFalse(file.isTruncated());
    }

    @Test
    void mediaFile_returnsGuardedDiskPath_andRejectsTraversalAndMissing() throws IOException {
        Files.writeString(root.resolve("pic.jpg"), "binary");
        importService.importFrom(root.toString());

        Path resolved = explorerService.mediaFile("pic.jpg");
        assertTrue(resolved.endsWith("pic.jpg"));
        assertTrue(resolved.startsWith(root));

        assertThrows(ExplorerPathOutOfRootException.class, () -> explorerService.mediaFile("../outside.jpg"));
        assertThrows(ExplorerFileNotFoundException.class, () -> explorerService.mediaFile("nope.jpg"));
    }

    @Test
    void file_outOfRoot_throws() {
        importService.importFrom(root.toString());
        assertThrows(ExplorerPathOutOfRootException.class, () -> explorerService.file("../outside.json"));
    }

    @Test
    void file_missing_throwsNotFound() {
        importService.importFrom(root.toString());
        assertThrows(ExplorerFileNotFoundException.class, () -> explorerService.file("missing.json"));
    }
}
