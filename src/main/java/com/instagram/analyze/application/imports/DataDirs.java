package com.instagram.analyze.application.imports;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * data/ 하위 경로 삭제 유틸 (재업로드 정리·retention 공용). 단일 사용자 로컬 앱이라 보안이 아니라
 * <b>실수 방지</b> 목적의 가드를 둔다: 삭제 대상은 반드시 {@code root} 의 직속 하위여야 한다
 * (루트 자체나 외부 경로 삭제 거부).
 */
public final class DataDirs {

    private DataDirs() {
    }

    /**
     * {@code target}(파일 또는 디렉토리)을 재귀 삭제한다. {@code root} 직속 하위가 아니면 거부한다.
     * 존재하지 않으면 무시한다.
     */
    public static void deleteWithinRoot(Path target, Path root) throws IOException {
        Path r = root.toAbsolutePath().normalize();
        Path t = target.toAbsolutePath().normalize();
        if (t.equals(r) || !r.equals(t.getParent())) {
            throw new IOException("refuse to delete outside data root: " + t);
        }
        if (!Files.exists(t)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(t)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);   // reverseOrder → 자식부터
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}
