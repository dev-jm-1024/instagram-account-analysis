package com.instagram.analyze.application.activity;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.instagram.analyze.domain.activity.ActivityType;
import com.instagram.analyze.domain.activity.CommentEntry;
import com.instagram.analyze.domain.activity.LikeEntry;
import com.instagram.analyze.domain.activity.Post;
import com.instagram.analyze.domain.activity.SavedPost;

/**
 * 활동 기록(4) 조회 서비스 (interface_plan §4.4). 메타데이터(timestamp·텍스트)만 다룬다.
 *
 * <p>전제: {@code requireCompleted()}.
 * <p>타임라인은 {@code List<? extends Timestamped>} 와일드카드 대신 <b>타입별 구체 반환</b>으로 제공해
 * Assembler 의 타입별 DTO 매핑을 단순화한다(§4.4 / E).
 */
public interface ActivityService {

    /** 타입별 총 개수. POST 는 게시물+스토리+릴스 합산. */
    long count(ActivityType type);

    /** 타입별 월별 카운트(월별 스택 막대그래프용). */
    Map<YearMonth, Long> monthlyCounts(ActivityType type);

    List<Post> posts();
    List<LikeEntry> likes();
    List<CommentEntry> comments();
    List<SavedPost> savedPosts();
}
