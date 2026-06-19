package com.instagram.analyze.domain.heatmap;

import com.instagram.analyze.domain.heatmap.vo.HeatmapPeak;

import lombok.Getter;

/**
 * 요일(7)×시(24) 활동 히트맵 (domain.md 5절).
 *
 * <p><b>중요</b>: 이 객체는 "사전계산 결과를 담는 그릇"이다. 도메인 모델(MessageStats 등)의 사후
 * 조합으로 만들 수 없다 — DM 은 24버킷 통계만 남겨 요일 축을 복원할 수 없기 때문이다.
 * 따라서 히트맵은 임포트 시점에 메모리에 살아있는 transient timestamp(게시물·좋아요·댓글·DM·로그인
 * 5종)로부터 1회 산출하여 이 객체에 담아 캐싱한다. grid 인덱스는 0=월 기준이다.
 */
@Getter
public final class ActivityHeatmap {

    private final int[][] grid; // [7][24], 0=월
    private final HeatmapPeak peak;

    public ActivityHeatmap(int[][] grid, HeatmapPeak peak) {
        this.grid = deepCopy(grid);
        this.peak = peak;
    }

    public int[][] getGrid() {
        return deepCopy(grid);
    }

    private static int[][] deepCopy(int[][] src) {
        if (src == null) {
            return new int[7][24];
        }
        int[][] copy = new int[src.length][];
        for (int i = 0; i < src.length; i++) {
            copy[i] = src[i].clone();
        }
        return copy;
    }
}
