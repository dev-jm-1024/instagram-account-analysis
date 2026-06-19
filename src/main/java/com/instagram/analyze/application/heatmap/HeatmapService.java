package com.instagram.analyze.application.heatmap;

import com.instagram.analyze.domain.heatmap.ActivityHeatmap;

/**
 * 활동 히트맵(5) 조회 서비스 (interface_plan §4.5).
 *
 * <p>자체 파싱 없이 import 시점에 사전계산·캐싱된 7×24 그리드를 반환한다(재계산 없음).
 * 입력 5종(게시물·좋아요·댓글·DM·로그인)의 timestamp 합산이며 DM 도 timestamp 만 쓰므로 owner 무관.
 */
public interface HeatmapService {

    /**
     * 7(요일)×24(시) 히트맵 + peak. {@code GET /api/heatmap}
     *
     * <p>전제: {@code requireCompleted()} → 미완료면 {@code IMPORT_NOT_COMPLETED}(503, G3).
     */
    ActivityHeatmap heatmap();
}
