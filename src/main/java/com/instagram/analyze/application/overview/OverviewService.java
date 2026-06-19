package com.instagram.analyze.application.overview;

import com.instagram.analyze.domain.overview.OverviewSummary;

/**
 * 개요 대시보드(9) 서비스 (interface_plan §4.9). 다른 도메인 집계를 조합한다(자체 파싱 없음).
 *
 * <p>구현은 아래 순서·소스 규약을 지켜야 한다:
 * <ol>
 *   <li><b>맨 먼저</b> {@code guard.isImportRequired()} 확인 → true 면 즉시
 *       {@code OverviewSummary(importRequired=true, ...)} <b>early-return</b>.
 *       이 분기 전에는 게이트 걸린 형제 서비스를 호출하지 않는다(503 누수 방지, A).</li>
 *   <li>맞팔 수 등은 {@code FollowService.analyze()} <b>재사용</b>(맞팔 계산 단일화).</li>
 *   <li>DM 카드(대화방 수·총 메시지)는 owner-게이트된 {@code MessageService.stats()} 대신
 *       {@code ImportReadStore.conversations()} 에서 <b>직접</b> 카운트(stats 우회, B).</li>
 *   <li>{@code mostActiveMonth} 는 {@code ActivityService.monthlyCounts()} <b>4타입을 머지</b>한 월별 합산 최댓값(E).</li>
 *   <li>{@code activityFrom/activityTo} 는 EpochMillis 를 가진 전 소스의 min/max —
 *       <b>로그인 포함, DM 제외</b>(DM 원시 ts 미보관, H).</li>
 * </ol>
 */
public interface OverviewService {

    /** 카드 집계. 미완료여도 에러 아님 → importRequired 응답(200). {@code GET /api/overview} */
    OverviewSummary overview();
}
