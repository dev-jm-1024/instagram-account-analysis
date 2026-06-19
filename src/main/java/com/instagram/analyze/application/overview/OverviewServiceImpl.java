package com.instagram.analyze.application.overview;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.activity.ActivityService;
import com.instagram.analyze.application.follow.FollowService;
import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.domain.activity.ActivityType;
import com.instagram.analyze.domain.common.Timestamped;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.follow.FollowAnalysis;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.domain.overview.OverviewSummary;

/**
 * {@link OverviewService} 구현 (interface_plan §4.9).
 *
 * <p>호출 순서: ① isImportRequired() early-return → ② 게이트 호환 형제만 재사용 + DM 은 store 직접.
 */
@Service
public class OverviewServiceImpl implements OverviewService {

    private final ImportReadStore store;
    private final ImportGuard guard;
    private final FollowService followService;
    private final ActivityService activityService;

    public OverviewServiceImpl(ImportReadStore store, ImportGuard guard,
                               FollowService followService, ActivityService activityService) {
        this.store = store;
        this.guard = guard;
        this.followService = followService;
        this.activityService = activityService;
    }

    @Override
    public OverviewSummary overview() {
        // ① 미완료면 형제 호출 전에 즉시 반환 (503 누수 방지)
        if (guard.isImportRequired()) {
            return new OverviewSummary(0, 0, 0, 0, 0, 0, 0L, 0L, 0L, null, null, null, true);
        }

        // ② 맞팔 등은 FollowService 재사용 (집계 단일화)
        FollowAnalysis follow = followService.analyze();

        // DM 카드는 owner-게이트된 stats() 대신 store.conversations() 직접 (owner-독립 카운트)
        List<Conversation> convs = store.conversations();
        long messageCount = convs.stream().mapToLong(Conversation::getTotalCount).sum();

        long likeCount = store.likes().size();
        long commentCount = store.comments().size();
        int totalPostCount = store.posts().size();  // post + story + reels

        String mostActiveMonth = computeMostActiveMonth();
        EpochMillis[] range = computeActivityRange();

        return new OverviewSummary(
                follow.getFollowerCount(),
                follow.getFollowingCount(),
                follow.getMutual().size(),
                follow.getIFollowOnly().size(),
                totalPostCount,
                convs.size(),
                messageCount,
                likeCount,
                commentCount,
                range[0],   // activityFrom (nullable)
                range[1],   // activityTo (nullable)
                mostActiveMonth,
                false);
    }

    /**
     * 4타입 월별 카운트를 머지해 최댓값 월을 yyyy-MM 으로. 데이터 없으면 null.
     * 동점이면 더 최근 월을 택해 결정적이게 한다(노트 4).
     */
    private String computeMostActiveMonth() {
        Map<YearMonth, Long> merged = new HashMap<>();
        for (ActivityType type : ActivityType.values()) {
            activityService.monthlyCounts(type)
                    .forEach((month, count) -> merged.merge(month, count, Long::sum));
        }
        return merged.entrySet().stream()
                .max(Map.Entry.<YearMonth, Long>comparingByValue()
                        .thenComparing(Map.Entry.comparingByKey()))   // 동점 → 최근 월
                .map(e -> e.getKey().toString())
                .orElse(null);
    }

    /**
     * 활동 기간(min/max)을 단일 패스로 산출(노트 3). 활동 timestamp 가 핵심인 소스만 포함:
     * 게시물·좋아요·댓글·저장·로그인. DM 은 원시 ts 미보관(H), <b>팔로우·검색은 timestamp 가 부차적·
     * nullable 이라 제외</b>(팔로우 0/미상이 1970 으로 오염되는 것 방지, H 재검토).
     *
     * @return [from, to] (둘 다 nullable)
     */
    private EpochMillis[] computeActivityRange() {
        List<List<? extends Timestamped>> sources = new ArrayList<>();
        sources.add(store.posts());
        sources.add(store.likes());
        sources.add(store.comments());
        sources.add(store.savedPosts());
        sources.add(store.loginEvents());

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        boolean any = false;
        for (List<? extends Timestamped> src : sources) {
            for (Timestamped t : src) {
                long v = t.getTimestamp().getValue();
                if (v < min) {
                    min = v;
                }
                if (v > max) {
                    max = v;
                }
                any = true;
            }
        }
        if (!any) {
            return new EpochMillis[]{null, null};
        }
        return new EpochMillis[]{EpochMillis.of(min), EpochMillis.of(max)};
    }
}
