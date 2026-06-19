package com.instagram.analyze.application.imports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.instagram.analyze.config.InstagramProperties;

/**
 * {@code data/} 루트를 얕게 스캔해 Instagram export 디렉토리 후보를 탐지한다
 * (260604_data_scan_plan_v1 §2.2). 사용자가 경로를 직접 입력하지 않아도 되게 하는 진입 보조.
 *
 * <p>각 후보는 marker 파일명(following.json, followers_*, message_*, posts_*, login_activity*)을
 * {@code instagram.data.max-scan-depth} 깊이 내에서 하나라도 가지면 export 로 간주한다 — 이는
 * {@code ImportValidator.validateExport} 의 "known 도메인 존재" 판정을 더 싸게(첫 marker 에서 단락)
 * 수행하는 것이며, 정식 검증은 임포트 시 {@code ImportPipeline.validateAndScan} 가 다시 수행한다.
 *
 * <p>탐지는 절대 throw 하지 않는다 — data/ 미존재·권한오류는 "후보 없음"(빈 목록)으로 흡수한다.
 */
@Service
public class ExportDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ExportDiscoveryService.class);

    /** 디렉토리명 {@code instagram-{account}-{yyyy-MM-dd}-{hash}} 에서 account·date 추출(best-effort). */
    private static final Pattern NAME_PATTERN =
            Pattern.compile("^instagram-(.*)-(\\d{4}-\\d{2}-\\d{2})-([A-Za-z0-9]+)$");

    private final InstagramProperties properties;

    public ExportDiscoveryService(InstagramProperties properties) {
        this.properties = properties;
    }

    /** 설정된 {@code instagram.data.root} 의 절대 경로(표시용). */
    public Path dataRoot() {
        return Path.of(properties.getData().getRoot()).toAbsolutePath().normalize();
    }

    public boolean autoImportSingle() {
        return properties.getData().isAutoImportSingle();
    }

    /**
     * data/ 직속 하위 디렉토리 중 export marker 를 가진 것을 후보로 수집한다.
     * 날짜 내림차순(최신 우선), 동률이면 이름 내림차순으로 정렬한다.
     */
    public List<ExportCandidate> discover() {
        Path root = dataRoot();
        if (!Files.isDirectory(root)) {
            return List.of();   // data/ 없음 → 후보 없음(에러 아님)
        }
        int maxDepth = Math.max(1, properties.getData().getMaxScanDepth());
        List<ExportCandidate> candidates = new ArrayList<>();
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory)
                    .filter(dir -> hasExportMarker(dir, maxDepth))
                    .forEach(dir -> candidates.add(toCandidate(dir)));
        } catch (IOException e) {
            log.warn("export discovery failed under {}", root, e);
            return List.of();
        }
        candidates.sort(Comparator
                .comparing(ExportCandidate::exportedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ExportCandidate::name, Comparator.reverseOrder()));
        return List.copyOf(candidates);
    }

    /** dir 하위 maxDepth 내에 export marker 파일이 하나라도 있으면 true(첫 발견 시 단락). */
    private boolean hasExportMarker(Path dir, int maxDepth) {
        try (Stream<Path> found = Files.find(dir, maxDepth,
                (path, attrs) -> attrs.isRegularFile() && isMarker(path.getFileName().toString()))) {
            return found.findAny().isPresent();
        } catch (IOException e) {
            return false;
        }
    }

    private boolean isMarker(String fileName) {
        String name = fileName.toLowerCase(Locale.ROOT);
        return name.equals("following.json")
                || name.startsWith("followers_")
                || name.startsWith("message_")
                || name.startsWith("posts_")
                || name.startsWith("login_activity");
    }

    private ExportCandidate toCandidate(Path dir) {
        String name = dir.getFileName().toString();
        String path = dir.toAbsolutePath().normalize().toString();
        Matcher m = NAME_PATTERN.matcher(name);
        if (m.matches()) {
            return new ExportCandidate(path, name, m.group(1), m.group(2));
        }
        return new ExportCandidate(path, name, null, null);
    }
}
