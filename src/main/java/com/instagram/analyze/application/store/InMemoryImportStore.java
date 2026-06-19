package com.instagram.analyze.application.store;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.instagram.analyze.application.support.ImportNotCompletedException;
import com.instagram.analyze.domain.activity.CommentEntry;
import com.instagram.analyze.domain.activity.LikeEntry;
import com.instagram.analyze.domain.activity.Post;
import com.instagram.analyze.domain.activity.SavedPost;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.ParseWarning;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.heatmap.ActivityHeatmap;
import com.instagram.analyze.domain.imports.ImportResult;
import com.instagram.analyze.domain.imports.ImportStatus;
import com.instagram.analyze.domain.log.LogFile;
import com.instagram.analyze.domain.login.LoginEvent;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.domain.search.SearchEntry;

/**
 * 인메모리 보관소 구현 — 읽기/쓰기 포트를 한 빈으로 제공한다 (interface_plan §3.1).
 *
 * <p>단일 사용자라 복잡한 동시성 제어는 불필요하다(domain.md 0.3). 다만 importFrom 이 논블로킹이라
 * 백그라운드 쓰기 ↔ 요청 스레드 읽기의 <b>가시성</b>만 보장하면 된다: 모든 상태 필드를 {@code volatile}
 * 로 두고, 쓰기 경로는 데이터 필드를 먼저 갱신한 뒤 <b>{@code importResult} 를 마지막에</b> 발행한다.
 * 조회는 게이트(requireCompleted)가 먼저 {@code importResult} 를 읽으므로 happens-before 가 성립한다.
 */
@Component
public class InMemoryImportStore implements ImportReadStore, ImportWritePort {

    private static final ImportResult IDLE_RESULT =
            new ImportResult(ImportStatus.IDLE, null, false, null, 0L, 0, List.of());

    private volatile Path importRoot;
    private volatile Map<DomainType, List<Path>> scannedFiles = Map.of();
    private volatile List<FollowEntry> followEntries = List.of();
    private volatile List<Conversation> conversations = List.of();
    private volatile List<Post> posts = List.of();
    private volatile List<LikeEntry> likes = List.of();
    private volatile List<CommentEntry> comments = List.of();
    private volatile List<SavedPost> savedPosts = List.of();
    private volatile List<LoginEvent> loginEvents = List.of();
    private volatile List<SearchEntry> searchEntries = List.of();
    private volatile List<LogFile> logFiles = List.of();
    private volatile ActivityHeatmap heatmap = new ActivityHeatmap(null, null);

    /** 발행 변수: 항상 마지막에 갱신한다. */
    private volatile ImportResult importResult = IDLE_RESULT;

    // ---------------- 쓰기 포트 ----------------

    @Override
    public void markInProgress(Path importRoot) {
        this.importRoot = importRoot;
        this.importResult = new ImportResult(ImportStatus.IN_PROGRESS,
                importResult.getOwner(), importResult.isOwnerResolved(), null, 0L, 0, List.of());
    }

    @Override
    public void markFailed(List<ParseWarning> warnings) {
        ImportResult prev = this.importResult;
        this.importResult = new ImportResult(ImportStatus.FAILED, prev.getOwner(), prev.isOwnerResolved(),
                null, prev.getDurationMillis(), prev.getParsedItemCount(), warnings);
    }

    @Override
    public void replaceAll(ImportSnapshot snapshot) {
        // 데이터 먼저, 발행(importResult) 마지막.
        this.followEntries = snapshot.getFollowEntries();
        this.conversations = snapshot.getConversations();
        this.posts = snapshot.getPosts();
        this.likes = snapshot.getLikes();
        this.comments = snapshot.getComments();
        this.savedPosts = snapshot.getSavedPosts();
        this.loginEvents = snapshot.getLoginEvents();
        this.searchEntries = snapshot.getSearchEntries();
        this.logFiles = snapshot.getLogFiles();
        this.heatmap = snapshot.getHeatmap() != null ? snapshot.getHeatmap() : new ActivityHeatmap(null, null);
        this.scannedFiles = snapshot.getScannedFiles();
        this.importResult = snapshot.getImportResult(); // status COMPLETED, 발행
    }

    @Override
    public void reset() {
        // 데이터 먼저 비우고 발행 변수(importResult)를 마지막에 IDLE 로 — replaceAll 과 동일한 발행 순서.
        this.importRoot = null;
        this.scannedFiles = Map.of();
        this.followEntries = List.of();
        this.conversations = List.of();
        this.posts = List.of();
        this.likes = List.of();
        this.comments = List.of();
        this.savedPosts = List.of();
        this.loginEvents = List.of();
        this.searchEntries = List.of();
        this.logFiles = List.of();
        this.heatmap = new ActivityHeatmap(null, null);
        this.importResult = IDLE_RESULT;
    }

    @Override
    public void applyOwner(AccountIdentity owner, List<Conversation> rebuiltConversations) {
        if (!isCompleted()) {
            // resolveOwner 가 완료 전 호출됨 → 도메인 예외(→503)로 매핑되게 한다(IllegalState→500 누수 방지).
            throw new ImportNotCompletedException();
        }
        this.conversations = List.copyOf(rebuiltConversations);
        ImportResult prev = this.importResult;
        this.importResult = new ImportResult(prev.getStatus(), owner, true, prev.getCompletedAt(),
                prev.getDurationMillis(), prev.getParsedItemCount(), prev.getWarnings());
    }

    // ---------------- 읽기 포트 ----------------

    @Override
    public ImportResult importResult() {
        return importResult;
    }

    @Override
    public boolean isCompleted() {
        return importResult.getStatus() == ImportStatus.COMPLETED;
    }

    @Override
    public boolean isOwnerResolved() {
        return importResult.isOwnerResolved();
    }

    @Override
    public Optional<AccountIdentity> owner() {
        return Optional.ofNullable(importResult.getOwner());
    }

    @Override
    public Optional<Path> importRoot() {
        return Optional.ofNullable(importRoot);
    }

    @Override
    public List<FollowEntry> followEntries() {
        return followEntries;
    }

    @Override
    public List<Conversation> conversations() {
        return conversations;
    }

    @Override
    public List<Post> posts() {
        return posts;
    }

    @Override
    public List<LikeEntry> likes() {
        return likes;
    }

    @Override
    public List<CommentEntry> comments() {
        return comments;
    }

    @Override
    public List<SavedPost> savedPosts() {
        return savedPosts;
    }

    @Override
    public List<LoginEvent> loginEvents() {
        return loginEvents;
    }

    @Override
    public List<SearchEntry> searchEntries() {
        return searchEntries;
    }

    @Override
    public List<LogFile> logFiles() {
        return logFiles;
    }

    @Override
    public ActivityHeatmap heatmap() {
        return heatmap;
    }

    @Override
    public boolean sourceExists(DomainType domain) {
        List<Path> files = scannedFiles.get(domain);
        return files != null && !files.isEmpty();
    }

    @Override
    public List<Path> scannedFiles(DomainType domain) {
        return scannedFiles.getOrDefault(domain, List.of());
    }
}
