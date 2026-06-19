package com.instagram.analyze.application.store;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.instagram.analyze.domain.activity.CommentEntry;
import com.instagram.analyze.domain.activity.LikeEntry;
import com.instagram.analyze.domain.activity.Post;
import com.instagram.analyze.domain.activity.SavedPost;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.heatmap.ActivityHeatmap;
import com.instagram.analyze.domain.imports.ImportResult;
import com.instagram.analyze.domain.log.LogFile;
import com.instagram.analyze.domain.login.LoginEvent;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.domain.search.SearchEntry;

import lombok.Builder;
import lombok.Getter;

/**
 * 임포트 1회의 산출물 묶음 (interface_plan §5). {@code ImportWritePort.replaceAll} 의 입력.
 *
 * <p>도메인별 파싱 결과 + 사전계산 히트맵 + <b>스캔된 파일 경로</b> 를 담는다.
 * scannedFiles 는 수동 본인식별 fallback 시 message 파일을 재파싱하기 위해 함께 보관한다(§4.1)
 * 이며, 도메인별 소스 존재 여부(G4 {@code sourceExists}) 판별에도 쓰인다.
 *
 * <p>메타데이터(owner·ownerResolved·completedAt·durationMillis·parsedItemCount·warnings)는 낱개로
 * 들지 않고 {@link ImportResult} 를 통째로 품는다 — 양쪽 동기화 누락을 막기 위함(리뷰 2 반영).
 * snapshot 의 importResult 는 status=COMPLETED 상태이며, store 는 적재 시 이를 그대로 반영한다.
 * 모든 컬렉션은 방어복사하여 불변으로 노출한다.
 */
@Getter
public final class ImportSnapshot {

    // 도메인별 파싱 결과
    private final List<FollowEntry> followEntries;
    private final List<Conversation> conversations;
    private final List<Post> posts;
    private final List<LikeEntry> likes;
    private final List<CommentEntry> comments;
    private final List<SavedPost> savedPosts;
    private final List<LoginEvent> loginEvents;
    private final List<SearchEntry> searchEntries;
    private final List<LogFile> logFiles;

    // 사전계산·메타
    private final ActivityHeatmap heatmap;     // import 시점 사전계산본 (5종 소스)
    private final ImportResult importResult;    // owner·ownerResolved·완료시각·소요·항목수·경고·status

    /** 도메인별 스캔된 파일 경로 (G4 sourceExists 판별 + owner fallback 재파싱용). */
    private final Map<DomainType, List<Path>> scannedFiles;

    @Builder
    public ImportSnapshot(List<FollowEntry> followEntries, List<Conversation> conversations,
                          List<Post> posts, List<LikeEntry> likes, List<CommentEntry> comments,
                          List<SavedPost> savedPosts, List<LoginEvent> loginEvents,
                          List<SearchEntry> searchEntries, List<LogFile> logFiles,
                          ActivityHeatmap heatmap, ImportResult importResult,
                          Map<DomainType, List<Path>> scannedFiles) {
        this.followEntries = copy(followEntries);
        this.conversations = copy(conversations);
        this.posts = copy(posts);
        this.likes = copy(likes);
        this.comments = copy(comments);
        this.savedPosts = copy(savedPosts);
        this.loginEvents = copy(loginEvents);
        this.searchEntries = copy(searchEntries);
        this.logFiles = copy(logFiles);
        this.heatmap = heatmap;
        this.importResult = importResult;
        this.scannedFiles = copyScanned(scannedFiles);
    }

    private static <E> List<E> copy(List<E> src) {
        return src == null ? List.of() : List.copyOf(src);
    }

    private static Map<DomainType, List<Path>> copyScanned(Map<DomainType, List<Path>> src) {
        if (src == null) {
            return Map.of();
        }
        Map<DomainType, List<Path>> copy = new LinkedHashMap<>();
        src.forEach((domain, paths) -> copy.put(domain, paths == null ? List.of() : List.copyOf(paths)));
        return Map.copyOf(copy);
    }
}
