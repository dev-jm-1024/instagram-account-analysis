package com.instagram.analyze.application.imports;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.instagram.analyze.application.store.ImportSnapshot;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.common.vo.Username;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.heatmap.ActivityHeatmap;
import com.instagram.analyze.domain.imports.ImportResult;
import com.instagram.analyze.domain.imports.ImportStatus;
import com.instagram.analyze.domain.log.LogFile;
import com.instagram.analyze.domain.login.LoginEvent;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.domain.search.SearchEntry;
import com.instagram.analyze.infrastructure.parse.ActivityBundle;
import com.instagram.analyze.infrastructure.parse.ActivityParser;
import com.instagram.analyze.infrastructure.parse.FollowParser;
import com.instagram.analyze.infrastructure.parse.HeatmapAccumulator;
import com.instagram.analyze.infrastructure.parse.LoginParser;
import com.instagram.analyze.infrastructure.parse.MessageParser;
import com.instagram.analyze.infrastructure.parse.MiscLogParser;
import com.instagram.analyze.infrastructure.parse.ParseWarnings;
import com.instagram.analyze.infrastructure.parse.SearchParser;
import com.instagram.analyze.infrastructure.scan.FileScanner;
import com.instagram.analyze.infrastructure.scan.ImportValidator;
import com.instagram.analyze.infrastructure.identity.AccountIdentityResolver;

/**
 * ETL 오케스트레이터(동기) — 검증 → 스캔 → 본인식별 → 파싱 → 히트맵 사전계산 → {@link ImportSnapshot}.
 *
 * <p>④c-1: 동기 단일 파이프라인(로직 검증용). ④c-2 에서 ImportService 가 이를 백그라운드로 감싸고
 * markInProgress→replaceAll/markFailed 로 비동기 전환한다. clock 은 호출자가 주입(테스트 결정성).
 */
@Component
public class ImportPipeline {

    private final ImportValidator validator;
    private final FileScanner scanner;
    private final AccountIdentityResolver identityResolver;
    private final FollowParser followParser;
    private final MessageParser messageParser;
    private final ActivityParser activityParser;
    private final LoginParser loginParser;
    private final SearchParser searchParser;
    private final MiscLogParser miscLogParser;
    private final ZoneId zone = ZoneId.systemDefault();

    public ImportPipeline(ImportValidator validator, FileScanner scanner,
                          AccountIdentityResolver identityResolver, FollowParser followParser,
                          MessageParser messageParser, ActivityParser activityParser,
                          LoginParser loginParser, SearchParser searchParser, MiscLogParser miscLogParser) {
        this.validator = validator;
        this.scanner = scanner;
        this.identityResolver = identityResolver;
        this.followParser = followParser;
        this.messageParser = messageParser;
        this.activityParser = activityParser;
        this.loginParser = loginParser;
        this.searchParser = searchParser;
        this.miscLogParser = miscLogParser;
    }

    /** 동기 전구간(검증→스캔→export 검증) — G1 실패는 여기서 throw(IN_PROGRESS 진입 전). */
    public Map<DomainType, List<Path>> validateAndScan(Path root) {
        validator.validatePath(root);
        Map<DomainType, List<Path>> scanned = scanner.scan(root);
        validator.validateExport(scanned, root);
        return scanned;
    }

    /** 동기 전구간 + 파싱을 한 번에(테스트·동기 호출용). */
    public ImportSnapshot run(Path root, long nowMillis) {
        return parse(validateAndScan(root), nowMillis, nowMillis);
    }

    /**
     * 파싱·집계 구간(백그라운드 실행 대상). 스캔된 파일맵을 받아 도메인 모델 + 히트맵 사전계산 → 스냅샷.
     *
     * @param startedAtMillis   임포트 시작 시각(소요 시간 계산)
     * @param completedAtMillis 완료 시각
     */
    public ImportSnapshot parse(Map<DomainType, List<Path>> scanned, long startedAtMillis, long completedAtMillis) {
        ParseWarnings warnings = new ParseWarnings();
        AccountIdentity owner = resolveOwner(scanned, warnings);

        List<FollowEntry> follows = followParser.parse(files(scanned, DomainType.FOLLOW), warnings);
        HeatmapAccumulator heatmap = new HeatmapAccumulator(zone);
        List<Conversation> conversations =
                messageParser.parse(files(scanned, DomainType.MESSAGE), owner, warnings, heatmap::add);

        // B3: owner 가 어느 sender 와도 안 맞으면(메시지는 있는데 보낸 수 0) 오식별이다 —
        // personal_information 의 이름/username 이 DM sender_name 형식과 달라 매칭 실패하는 엣지.
        // ① participants 교집합으로 재시도(교집합 이름은 sender_name 과 같은 표시이름 형식) →
        // ② 그래도 안 맞으면 미해결로 두어 수동 입력 유도. 재집계는 히트맵 이미 반영(no-op)·경고 중복 방지(throwaway).
        if (owner != null && ownerMatchesNoSender(conversations)) {
            List<Path> messageFiles = files(scanned, DomainType.MESSAGE);
            String mismatchedName = owner.getDisplayName();
            AccountIdentity byParticipants = messageParser
                    .resolveOwnerByParticipants(messageFiles, new ParseWarnings())
                    .map(name -> new AccountIdentity(Username.of(name), name))
                    .filter(candidate -> !candidate.getDisplayName().equals(mismatchedName))
                    .orElse(null);
            if (byParticipants != null) {
                owner = byParticipants;
                conversations = messageParser.parse(messageFiles, owner, new ParseWarnings(), t -> { });
            }
            if (ownerMatchesNoSender(conversations)) {
                owner = null;
                conversations = messageParser.parse(messageFiles, null, new ParseWarnings(), t -> { });
            }
        }
        ActivityBundle activity = activityParser.parse(files(scanned, DomainType.ACTIVITY), warnings);
        List<LoginEvent> logins = loginParser.parse(files(scanned, DomainType.LOGIN), warnings);
        List<SearchEntry> searches = searchParser.parse(files(scanned, DomainType.SEARCH), warnings);
        List<LogFile> logs = miscLogParser.parse(files(scanned, DomainType.MISC_LOG), warnings);

        // 히트맵 5종: DM(싱크로 이미 반영) + 게시물·좋아요·댓글 + 로그인
        activity.getPosts().forEach(p -> heatmap.add(p.getTimestamp()));
        activity.getLikes().forEach(l -> heatmap.add(l.getTimestamp()));
        activity.getComments().forEach(c -> heatmap.add(c.getTimestamp()));
        logins.forEach(l -> heatmap.add(l.getTimestamp()));
        ActivityHeatmap activityHeatmap = heatmap.build();

        long messageCount = conversations.stream().mapToLong(Conversation::getTotalCount).sum();
        int parsedItemCount = follows.size() + activity.getPosts().size() + activity.getLikes().size()
                + activity.getComments().size() + activity.getSavedPosts().size()
                + logins.size() + searches.size() + (int) messageCount;

        ImportResult result = new ImportResult(ImportStatus.COMPLETED, owner, owner != null,
                EpochMillis.of(completedAtMillis), Math.max(0L, completedAtMillis - startedAtMillis),
                parsedItemCount, warnings.toList());

        return ImportSnapshot.builder()
                .followEntries(follows)
                .conversations(conversations)
                .posts(activity.getPosts())
                .likes(activity.getLikes())
                .comments(activity.getComments())
                .savedPosts(activity.getSavedPosts())
                .loginEvents(logins)
                .searchEntries(searches)
                .logFiles(logs)
                .heatmap(activityHeatmap)
                .importResult(result)
                .scannedFiles(scanned)
                .build();
    }

    /** ① personal_information.json → ② DM participants 교집합(정확히 1명일 때만) → 실패 시 null. */
    private AccountIdentity resolveOwner(Map<DomainType, List<Path>> scanned, ParseWarnings warnings) {
        Optional<AccountIdentity> byInfo = identityResolver.resolve(scanned);
        if (byInfo.isPresent()) {
            return byInfo.get();
        }
        return messageParser.resolveOwnerByParticipants(files(scanned, DomainType.MESSAGE), warnings)
                .map(name -> new AccountIdentity(Username.of(name), name))
                .orElse(null);
    }

    /** 메시지는 있는데 본인이 보낸 수가 0이면 owner 가 어느 sender 와도 안 맞은 것(B3). */
    private boolean ownerMatchesNoSender(List<Conversation> conversations) {
        long messages = conversations.stream().mapToLong(Conversation::getTotalCount).sum();
        long sent = conversations.stream().mapToLong(Conversation::getSentCount).sum();
        return messages > 0 && sent == 0;
    }

    private List<Path> files(Map<DomainType, List<Path>> scanned, DomainType domain) {
        return scanned.getOrDefault(domain, List.of());
    }
}
