package com.instagram.analyze.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.instagram.analyze.application.activity.ActivityServiceImpl;
import com.instagram.analyze.application.follow.FollowServiceImpl;
import com.instagram.analyze.application.heatmap.HeatmapServiceImpl;
import com.instagram.analyze.application.imports.StubImportService;
import com.instagram.analyze.application.message.MessageServiceImpl;
import com.instagram.analyze.application.overview.OverviewServiceImpl;
import com.instagram.analyze.application.search.SearchServiceImpl;
import com.instagram.analyze.application.store.InMemoryImportStore;
import com.instagram.analyze.application.support.DefaultImportGuard;
import com.instagram.analyze.application.support.ImportNotCompletedException;
import com.instagram.analyze.application.support.OwnerNotResolvedException;
import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.heatmap.ActivityHeatmap;
import com.instagram.analyze.domain.imports.ImportStatus;
import com.instagram.analyze.domain.message.MessageStats;
import com.instagram.analyze.domain.overview.OverviewSummary;
import com.instagram.analyze.domain.search.vo.SearchFrequency;

/**
 * interface_plan §8 검증 — 게이트·전이·G4·null-peak 를 stub 으로 묶어 확인.
 */
class ServiceGatesTest {

    private InMemoryImportStore store;
    private StubImportService importService;
    private OverviewServiceImpl overviewService;
    private HeatmapServiceImpl heatmapService;
    private MessageServiceImpl messageService;
    private SearchServiceImpl searchService;

    @BeforeEach
    void setUp() {
        store = new InMemoryImportStore();
        DefaultImportGuard guard = new DefaultImportGuard(store);
        importService = new StubImportService(store, store);
        FollowServiceImpl followService = new FollowServiceImpl(store, guard);
        ActivityServiceImpl activityService = new ActivityServiceImpl(store, guard);
        overviewService = new OverviewServiceImpl(store, guard, followService, activityService);
        heatmapService = new HeatmapServiceImpl(store, guard);
        messageService = new MessageServiceImpl(store, guard, new com.instagram.analyze.config.InstagramProperties());
        searchService = new SearchServiceImpl(store, guard);
    }

    @Test
    void idle_overviewReturnsImportRequired_withoutCallingGatedSiblings() {
        OverviewSummary summary = overviewService.overview();
        assertTrue(summary.isImportRequired());
    }

    @Test
    void idle_heatmapThrows503Gate() {
        assertThrows(ImportNotCompletedException.class, heatmapService::heatmap);
    }

    @Test
    void importFrom_transitionsToCompleted_andStatusReflectsIt() {
        assertEquals(ImportStatus.COMPLETED, importService.importFrom("/some/export").getStatus());
        assertEquals(ImportStatus.COMPLETED, importService.status().getStatus());
        assertFalse(overviewService.overview().isImportRequired());
    }

    @Test
    void completedButOwnerUnresolved_statsThrows_noPartialResponse() {
        importService.importFrom("/x");
        assertFalse(store.isOwnerResolved());
        assertThrows(OwnerNotResolvedException.class, messageService::stats);
    }

    @Test
    void resolveOwner_thenStatsSucceeds() {
        importService.importFrom("/x");
        importService.resolveOwner("me");
        assertTrue(store.isOwnerResolved());
        MessageStats stats = messageService.stats();   // no throw
        assertEquals(0, stats.getTotalRooms());
    }

    @Test
    void emptyImport_searchSourceAbsent() {
        importService.importFrom("/x");
        Sourced<List<SearchFrequency>> result = searchService.frequencies();
        assertFalse(result.isSourceExists());
        assertTrue(result.getValue().isEmpty());
    }

    @Test
    void emptyImport_heatmapHasNullPeakButNonNullGrid() {   // 리뷰 A
        importService.importFrom("/x");
        ActivityHeatmap h = heatmapService.heatmap();
        assertNull(h.getPeak());
        assertNotNull(h.getGrid());
        assertEquals(7, h.getGrid().length);
        assertEquals(24, h.getGrid()[0].length);
    }

    @Test
    void overview_afterEmptyImport_hasZeroCountsAndNullRange() {
        importService.importFrom("/x");
        OverviewSummary summary = overviewService.overview();
        assertFalse(summary.isImportRequired());
        assertEquals(0, summary.getFollowerCount());
        assertNull(summary.getActivityFrom());
        assertNull(summary.getMostActiveMonth());
    }
}
