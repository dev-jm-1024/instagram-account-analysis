package com.instagram.analyze.application.store;

import java.nio.file.Path;
import java.util.List;

import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.ParseWarning;
import com.instagram.analyze.domain.message.Conversation;

/**
 * 인메모리 보관소 <b>쓰기 포트</b> (interface_plan §3.1). {@code ImportService} 전용.
 *
 * <p>임포트 상태 전이도 store 가 단일 진실로 들고 있어야 한다 — {@code ImportService.status()}(폴링)와
 * {@code ImportGuard} 가 모두 {@link ImportReadStore} 를 읽기 때문이다. 따라서 IDLE→IN_PROGRESS→
 * COMPLETED/FAILED 전이를 쓰기 포트에 명시한다. 쓰기 경로(전이 / 전체 교체 / owner fallback)를 격리한다.
 *
 * <pre>
 *   importFrom() ─ markInProgress() ─┬─ (완료) replaceAll(snapshot)   → COMPLETED
 *                                    └─ (치명오류) markFailed(warnings) → FAILED
 *   resolveOwner() ─ applyOwner(owner, rebuilt)
 * </pre>
 */
public interface ImportWritePort {

    /**
     * 임포트 시작 시 상태를 IN_PROGRESS 로 전이하고 루트 경로를 캡처한다(논블로킹 진입, §4.1 G).
     * 루트는 이후 {@code ImportReadStore.importRoot()} 로 노출되어 ExplorerService 가 사용한다.
     */
    void markInProgress(Path importRoot);

    /** 백그라운드 파싱이 치명 오류로 중단되면 FAILED 로 전이하고 누적 경고를 보관한다. */
    void markFailed(List<ParseWarning> warnings);

    /**
     * 임포트/재임포트 결과를 통째로 교체하며 상태를 COMPLETED 로 전이한다(원자 교체 = markCompleted).
     * snapshot.importResult 의 status 는 COMPLETED 이며 store 가 이를 그대로 반영한다.
     */
    void replaceAll(ImportSnapshot snapshot);

    /**
     * 수동 본인식별 fallback 적용. conversations 교체뿐 아니라 부수효과로
     * <ul>
     *   <li>store 의 owner 를 주어진 owner 로 채우고</li>
     *   <li>{@code ImportResult.ownerResolved} 를 true 로 전환한다.</li>
     * </ul>
     * rebuiltConversations 는 {@code ImportService} 가 보관된 message 파일 경로를 재파싱하여
     * owner-의존 필드(sentCount·receivedCount·ownerInitiated)까지 다시 채운 결과다(§4.3).
     *
     * <p><b>전제: COMPLETED 상태에서만 호출한다.</b> resolveOwner 는 임포트 완료 후의 fallback 이므로
     * IN_PROGRESS/FAILED 에서의 호출은 정의되지 않는다(구현은 가드를 두는 것을 권장).
     */
    void applyOwner(AccountIdentity owner, List<Conversation> rebuiltConversations);

    /**
     * 인메모리 데이터를 전부 비우고 IDLE 로 되돌린다(데이터 삭제/초기화). 루트·스냅샷·상태 모두 초기화 →
     * 이후 조회는 {@code ImportGuard} 에서 미완료로 막히고 대시보드는 잠긴다(재임포트 전까지).
     */
    void reset();
}
