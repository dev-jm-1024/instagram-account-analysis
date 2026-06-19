package com.instagram.analyze.infrastructure.scan;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.instagram.analyze.application.imports.ImportValidationException;
import com.instagram.analyze.application.imports.ImportValidationException.Reason;
import com.instagram.analyze.domain.common.DomainType;

/**
 * 임포트 경로·포맷 검증 (domain.md 1 / domain_exception G1). 백그라운드 진입 전 동기로 수행.
 */
@Component
public class ImportValidator {

    private static final List<DomainType> KNOWN = List.of(
            DomainType.FOLLOW, DomainType.MESSAGE, DomainType.ACTIVITY, DomainType.LOGIN);

    /** 경로 존재·디렉토리 검증(스캔 전). */
    public void validatePath(Path root) {
        if (!Files.exists(root)) {
            throw new ImportValidationException(Reason.PATH_NOT_FOUND, "path does not exist: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new ImportValidationException(Reason.PATH_NOT_DIRECTORY, "path is not a directory: " + root);
        }
    }

    /** Instagram export 포맷 검증(스캔 후). 알려진 도메인 JSON 이 없으면 HTML-only / 비-export 구분. */
    public void validateExport(Map<DomainType, List<Path>> scanned, Path root) {
        boolean hasKnown = KNOWN.stream().anyMatch(d -> !scanned.getOrDefault(d, List.of()).isEmpty());
        if (hasKnown) {
            return;
        }
        if (hasHtmlButNoJson(root)) {
            throw new ImportValidationException(Reason.HTML_ONLY, "only .html files found, no json");
        }
        throw new ImportValidationException(Reason.NOT_INSTAGRAM_EXPORT, "no instagram export pattern found");
    }

    private boolean hasHtmlButNoJson(Path root) {
        boolean html = false;
        boolean json = false;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                if (name.endsWith(".html")) {
                    html = true;
                }
                if (name.endsWith(".json")) {
                    json = true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return html && !json;
    }
}
