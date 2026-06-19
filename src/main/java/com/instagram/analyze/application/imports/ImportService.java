package com.instagram.analyze.application.imports;

import com.instagram.analyze.domain.imports.ImportResult;

/**
 * 임포트(1) / ETL 진입점 — 앱에서 유일한 쓰기 경로 (interface_plan §4.1).
 *
 * <p>경로 검증 → 스캔 → 스트리밍 파싱·정규화 → 본인식별 → 보관소 적재 → 히트맵 사전계산.
 * 협력자(FileScanner·StringNormalizer·AccountIdentityResolver)는 {@code infrastructure} 에 있다.
 */
public interface ImportService {

    /**
     * 임포트 시작. <b>논블로킹</b>이다(§4.1 G 해소).
     * <ul>
     *   <li>경로·export 포맷 검증(G1)만 호출 스레드에서 동기 수행 — 실패 시 즉시 400/422 (IN_PROGRESS 진입 안 함)</li>
     *   <li>검증 통과 시 무거운 파싱은 백그라운드로 실행하고 즉시 {@code ImportResult(status=IN_PROGRESS)} 반환</li>
     *   <li>이후 프론트는 {@link #status()} 를 폴링해 진행률·완료(COMPLETED/FAILED)를 받는다</li>
     * </ul>
     *
     * @param folderPath 압축 해제한 export 폴더 경로
     */
    ImportResult importFrom(String folderPath);

    /**
     * 현재 임포트 상태. 프론트 폴링 대상.
     *
     * <p>진행률은 <b>count 기반</b>이다 — {@code ImportResult.parsedItemCount}("12,340건 파싱")만 노출하고
     * 총 예상 개수(분모)는 두지 않는다. 단일 사용자라 퍼센트 게이지 대신 누적 카운트로 충분하다.
     * (퍼센트가 필요해지면 스캔 단계에서 총 항목 추정치를 ImportResult 에 추가)
     */
    ImportResult status();

    /**
     * 자동 본인식별 실패 시 수동 username 으로 owner 를 확정한다(fallback, §4.1).
     *
     * <p>보관된 message 파일 경로를 재파싱하여 DM owner-의존 집계를 다시 빌드하고
     * {@code ImportWritePort.applyOwner} 로 교체한다. 원본 폴더가 디스크에 없어 재파싱이 실패하면
     * owner 확정을 거부하고 재임포트를 안내한다(C).
     *
     * @param username 본인 username (blank → {@code OWNER_INPUT_BLANK} 400)
     */
    ImportResult resolveOwner(String username);

    /**
     * 인메모리 데이터를 전부 비우고 IDLE 로 되돌린다(데이터 삭제). 재임포트 전까지 조회 메뉴는 잠긴다.
     * 인메모리라 디스크의 export 파일은 건드리지 않는다(다시 임포트하면 복구).
     *
     * @return 초기화된 상태(IDLE)
     */
    ImportResult reset();
}
