package com.instagram.analyze.application.store;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.instagram.analyze.domain.activity.CommentEntry;
import com.instagram.analyze.domain.activity.LikeEntry;
import com.instagram.analyze.domain.activity.Post;
import com.instagram.analyze.domain.activity.SavedPost;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.heatmap.ActivityHeatmap;
import com.instagram.analyze.domain.imports.ImportResult;
import com.instagram.analyze.domain.log.LogFile;
import com.instagram.analyze.domain.login.LoginEvent;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.domain.search.SearchEntry;

/**
 * 인메모리 보관소 <b>읽기 포트</b> (interface_plan §3.1).
 *
 * <p>조회 서비스 9종이 의존한다. 쓰기 메서드는 노출하지 않아 조회 측이 데이터를 변경할 수 없다(ISP).
 */
public interface ImportReadStore {

    // --- 상태 ---
    ImportResult importResult();
    boolean isCompleted();
    boolean isOwnerResolved();
    Optional<AccountIdentity> owner();

    /** 임포트 루트 경로(importFrom 시 캡처). 미임포트(IDLE) 시 empty. ExplorerService 가 디스크 접근에 사용. */
    Optional<Path> importRoot();

    /** 도메인별 스캔된 파일 경로(resolveOwner 의 message 재파싱 등에 사용). 없으면 빈 리스트. */
    List<Path> scannedFiles(DomainType domain);

    // --- 도메인별 읽기 (원본 컬렉션) ---
    List<FollowEntry> followEntries();
    List<Conversation> conversations();   // owner 미해결 시 owner-독립 필드만 채워진 상태
    List<Post> posts();
    List<LikeEntry> likes();
    List<CommentEntry> comments();
    List<SavedPost> savedPosts();
    List<LoginEvent> loginEvents();
    List<SearchEntry> searchEntries();
    List<LogFile> logFiles();
    /**
     * import 시점 사전계산본 (owner 무관). 정상 흐름에선 {@code requireCompleted()} 로 사전 게이트되나,
     * 임포트 전/실패 시에도 NPE 를 피하기 위해 <b>null 이 아닌 빈 7×24 히트맵</b>을 반환한다.
     */
    ActivityHeatmap heatmap();

    /**
     * 해당 도메인의 소스 파일이 export 에 존재했는지(primitive 사실).
     * 서비스가 이 boolean 으로 결과를 {@code Sourced<T>} 로 래핑해 G4 계약화한다.
     */
    boolean sourceExists(DomainType domain);
}
