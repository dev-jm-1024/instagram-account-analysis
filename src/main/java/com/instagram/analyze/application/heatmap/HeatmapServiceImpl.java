package com.instagram.analyze.application.heatmap;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.domain.heatmap.ActivityHeatmap;

/**
 * {@link HeatmapService} 구현. import 시점 사전계산본을 그대로 반환한다.
 *
 * <p><b>주의(A)</b>: 빈 export(또는 stub)에서는 grid 가 비어 있고 {@code peak == null} 일 수 있다.
 * grid 는 항상 non-null 7×24 이지만 peak 는 null 가능하므로, 이를 소비하는 api Assembler
 * ({@code HeatmapResponse})는 반드시 peak null-guard 를 해야 한다.
 */
@Service
public class HeatmapServiceImpl implements HeatmapService {

    private final ImportReadStore store;
    private final ImportGuard guard;

    public HeatmapServiceImpl(ImportReadStore store, ImportGuard guard) {
        this.store = store;
        this.guard = guard;
    }

    @Override
    public ActivityHeatmap heatmap() {
        guard.requireCompleted();   // 미완료 → IMPORT_NOT_COMPLETED(503)
        return store.heatmap();     // grid non-null, peak 는 null 가능 (빈 데이터)
    }
}
