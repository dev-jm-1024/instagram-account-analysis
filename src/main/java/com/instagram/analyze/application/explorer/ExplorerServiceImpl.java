package com.instagram.analyze.application.explorer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.config.InstagramProperties;
import com.instagram.analyze.infrastructure.text.StringNormalizer;
import com.instagram.analyze.domain.explorer.ExplorerNode;
import com.instagram.analyze.domain.explorer.RawFileContent;

/**
 * {@link ExplorerService} 구현 — store 가 아니라 디스크(임포트 루트)를 직접 읽는다.
 *
 * <p>미디어 확장자 제외, 최대 깊이 10, 10MB 초과 시 truncate, 루트 외부/미존재는 G5 예외.
 * 파일 내용은 UTF-8 로 읽고 {@link StringNormalizer} 로 mojibake 보정(§1.2/§10) — normalizer 의
 * 0xFF 가드 덕에 정상 UTF-8 은 그대로, latin1-misread 만 복원된다.
 */
@Service
public class ExplorerServiceImpl implements ExplorerService {

    private final ImportReadStore store;
    private final ImportGuard guard;
    private final StringNormalizer normalizer;
    private final int maxDepth;
    private final long maxBytes;

    public ExplorerServiceImpl(ImportReadStore store, ImportGuard guard, StringNormalizer normalizer,
                              InstagramProperties properties) {
        this.store = store;
        this.guard = guard;
        this.normalizer = normalizer;
        this.maxDepth = properties.getExplorer().getMaxDepth();
        this.maxBytes = properties.getExplorer().getMaxFileBytes();
    }

    @Override
    public ExplorerNode tree() {
        guard.requireCompleted();
        Path root = root();
        return buildNode(root, root, 0);
    }

    @Override
    public RawFileContent file(String path) {
        Path target = resolveWithinRoot(path);   // 게이트·가드 공통
        Path root = root();
        try {
            long size = Files.size(target);
            boolean truncated = size > maxBytes;
            byte[] bytes;
            try (InputStream in = Files.newInputStream(target)) {
                bytes = in.readNBytes((int) Math.min(size, maxBytes));
            }
            String content = normalizer.normalize(new String(bytes, StandardCharsets.UTF_8));
            return new RawFileContent(root.relativize(target).toString(), content, truncated);
        } catch (IOException e) {
            throw new ExplorerFileNotFoundException(path);
        }
    }

    @Override
    public Path mediaFile(String path) {
        return resolveWithinRoot(path);   // 내용은 안 읽고 가드된 경로만 — 컨트롤러가 스트리밍
    }

    /** 루트 경로. 방어적: COMPLETED 인데 루트 미설정이면 ExplorerNotImportedException (B). */
    private Path root() {
        return store.importRoot().orElseThrow(ExplorerNotImportedException::new);
    }

    /** 게이트(requireCompleted) + 루트 내부 가드(zip-slip 방지) + 존재/파일 검사. file·media 공통. */
    private Path resolveWithinRoot(String path) {
        guard.requireCompleted();
        Path root = root();
        Path target = root.resolve(path == null ? "" : path).normalize();
        if (!target.startsWith(root)) {
            throw new ExplorerPathOutOfRootException(path);   // G5 → 400
        }
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw new ExplorerFileNotFoundException(path);    // G5 → 404
        }
        return target;
    }

    private ExplorerNode buildNode(Path root, Path path, int depth) {
        boolean directory = Files.isDirectory(path);
        String name = path.getFileName() != null ? path.getFileName().toString() : path.toString();
        String relativePath = root.relativize(path).toString();

        List<ExplorerNode> children = new ArrayList<>();
        if (directory && depth < maxDepth) {
            try (Stream<Path> entries = Files.list(path)) {
                // 미디어(jpg/webp/mp4 등)도 포함 — 탐색기에서 이미지/영상 미리보기를 위해(미디어 엔드포인트로 스트리밍).
                entries.sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(child -> children.add(buildNode(root, child, depth + 1)));
            } catch (IOException e) {
                // 읽을 수 없는 디렉토리는 children 비움
            }
        }
        return new ExplorerNode(name, relativePath, directory, children);
    }
}
