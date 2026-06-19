package com.instagram.analyze.application.activity;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.domain.activity.ActivityType;
import com.instagram.analyze.domain.activity.CommentEntry;
import com.instagram.analyze.domain.activity.LikeEntry;
import com.instagram.analyze.domain.activity.Post;
import com.instagram.analyze.domain.activity.SavedPost;
import com.instagram.analyze.domain.common.Timestamped;

/**
 * {@link ActivityService} 구현. 월별 집계 시간대는 시스템 로컬(부록 C).
 */
@Service
public class ActivityServiceImpl implements ActivityService {

    private final ImportReadStore store;
    private final ImportGuard guard;
    private final ZoneId zone = ZoneId.systemDefault();

    public ActivityServiceImpl(ImportReadStore store, ImportGuard guard) {
        this.store = store;
        this.guard = guard;
    }

    @Override
    public long count(ActivityType type) {
        guard.requireCompleted();
        return entriesOf(type).size();
    }

    @Override
    public Map<YearMonth, Long> monthlyCounts(ActivityType type) {
        guard.requireCompleted();
        return entriesOf(type).stream().collect(Collectors.groupingBy(
                e -> YearMonth.from(e.getTimestamp().toLocalDateTime(zone)),
                Collectors.counting()));
    }

    @Override
    public List<Post> posts() {
        guard.requireCompleted();
        return store.posts();
    }

    @Override
    public List<LikeEntry> likes() {
        guard.requireCompleted();
        return store.likes();
    }

    @Override
    public List<CommentEntry> comments() {
        guard.requireCompleted();
        return store.comments();
    }

    @Override
    public List<SavedPost> savedPosts() {
        guard.requireCompleted();
        return store.savedPosts();
    }

    /** 타입별 원본 엔트리(POST 는 게시물+스토리+릴스 전부 = store.posts()). */
    private List<? extends Timestamped> entriesOf(ActivityType type) {
        return switch (type) {
            case POST -> store.posts();
            case LIKE -> store.likes();
            case COMMENT -> store.comments();
            case SAVED -> store.savedPosts();
        };
    }
}
