package com.instagram.analyze.application.explorer;

import java.nio.file.Path;

import com.instagram.analyze.domain.explorer.ExplorerNode;
import com.instagram.analyze.domain.explorer.RawFileContent;

/**
 * 데이터 탐색기(10) 서비스 (interface_plan §4.10). 원본 트리/파일 fallback.
 *
 * <p>다른 8개 조회 서비스와 달리 store 컬렉션이 아니라 <b>디스크(임포트 루트)</b>를 직접 읽는다.
 * 루트는 importFrom 시 캡처되어 {@code ImportReadStore.importRoot()} 로 노출된다.
 * <p><b>전제: {@code requireCompleted()}</b> (메뉴 잠금 일관성, domain.md 0.3). 미임포트(IDLE)면
 * 루트가 없으므로 게이트에서 차단된다 — 즉 루트 미설정 분기는 G5 가 아니라 게이트로 닫는다.
 */
public interface ExplorerService {

    /** 임포트 루트 기준 디렉토리 트리(미디어 포함, 최대 깊이 10). {@code GET /api/explorer/tree} */
    ExplorerNode tree();

    /**
     * 미디어/바이너리 원본 스트리밍용 디스크 경로(루트 내부로 가드). {@code GET /api/explorer/media?path=...}
     * 내용을 읽지 않고 경로만 돌려 컨트롤러가 바이트 스트리밍한다(이미지/영상 미리보기).
     *
     * <p>예외(G5): 루트 외부 → {@code EXPLORER_PATH_OUT_OF_ROOT}(400) / 미존재 → {@code EXPLORER_FILE_NOT_FOUND}(404).
     */
    Path mediaFile(String path);

    /**
     * 단일 파일 원본 JSON(구조 raw, 문자열만 정규화, 10MB 초과 시 truncate). {@code GET /api/explorer/file?path=...}
     *
     * <p>예외(G5): 루트 외부 경로 → {@code EXPLORER_PATH_OUT_OF_ROOT}(400) /
     * 미존재 → {@code EXPLORER_FILE_NOT_FOUND}(404).
     */
    RawFileContent file(String path);
}
