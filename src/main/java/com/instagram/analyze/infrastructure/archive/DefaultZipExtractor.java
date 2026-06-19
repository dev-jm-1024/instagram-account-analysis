package com.instagram.analyze.infrastructure.archive;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.IntConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.stereotype.Component;

/**
 * {@link ZipExtractor} 구현 — {@link ZipInputStream} 스트리밍 추출.
 *
 * <p>각 엔트리 대상 경로가 {@code targetDir} 안쪽으로 normalize 되는지 검사해 zip-slip 을 막는다
 * (방어적 안전장치 — 보안 목적 아님, domain.md 0 톤과 일치). 메모리는 8KB 버퍼만 사용.
 */
@Component
public class DefaultZipExtractor implements ZipExtractor {

    @Override
    public int extract(Path zip, Path targetDir, IntConsumer onEntry) throws IOException {
        Path root = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(root);
        int count = 0;
        try (InputStream in = new BufferedInputStream(Files.newInputStream(zip));
             ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path resolved = root.resolve(entry.getName()).normalize();
                if (!resolved.startsWith(root)) {
                    throw new IOException("zip-slip blocked: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                } else {
                    Files.createDirectories(resolved.getParent());
                    Files.copy(zis, resolved, StandardCopyOption.REPLACE_EXISTING);
                    count++;
                    if (onEntry != null) {
                        onEntry.accept(count);
                    }
                }
                zis.closeEntry();
            }
        }
        return count;
    }
}
