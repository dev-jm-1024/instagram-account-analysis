package com.instagram.analyze.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.infrastructure.archive.DefaultZipExtractor;

class DefaultZipExtractorTest {

    @TempDir
    Path work;

    private final DefaultZipExtractor extractor = new DefaultZipExtractor();

    @Test
    void extractsAllEntries_includingNestedAndMedia() throws IOException {
        Path zip = work.resolve("export.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            put(zos, "connections/followers_and_following/following.json", "{}");
            put(zos, "media/posts/pic.jpg", "binary");   // 미디어도 전체 추출
        }
        Path target = work.resolve("out");

        int count = extractor.extract(zip, target, null);

        assertEquals(2, count);
        assertTrue(Files.exists(target.resolve("connections/followers_and_following/following.json")));
        assertTrue(Files.exists(target.resolve("media/posts/pic.jpg")));
    }

    @Test
    void blocksZipSlipEntry() throws IOException {
        Path zip = work.resolve("evil.zip");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zip))) {
            put(zos, "../escaped.txt", "pwned");
        }
        Path target = work.resolve("out");

        assertThrows(IOException.class, () -> extractor.extract(zip, target, null));
        assertFalse(Files.exists(work.resolve("escaped.txt")));   // 루트 밖으로 안 써짐
    }

    private void put(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes());
        zos.closeEntry();
    }
}
