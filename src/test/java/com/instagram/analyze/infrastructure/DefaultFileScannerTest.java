package com.instagram.analyze.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.config.ScanMappingProperties;
import com.instagram.analyze.domain.common.DomainType;
import com.instagram.analyze.infrastructure.scan.DefaultFileScanner;

class DefaultFileScannerTest {

    @TempDir
    Path root;

    // 실제 번들 매핑(instagram-schema-mapping.yaml)을 로드해 운영과 동일 규칙으로 검증
    private final DefaultFileScanner scanner =
            new DefaultFileScanner(ScanMappingProperties.fromClasspathYaml());

    @BeforeEach
    void layout() throws IOException {
        write("connections/followers_and_following/following.json", "[]");
        write("connections/followers_and_following/followers_1.json", "[]");
        write("your_instagram_activity/messages/inbox/foo/message_1.json", "{}");
        write("security_and_login_information/login_and_profile_creation/login_activity.json", "{}");
        write("logged_information/account_searches.json", "{}");      // 검색이 먼저 claim
        write("logged_information/some_other_log.json", "{}");        // 나머지 → MISC_LOG
        write("personal_information/personal_information.json", "{}");
        write("your_instagram_activity/media/pic.jpg", "binarymedia"); // 미디어 제외
        write("mystery.json", "{}");                                   // UNKNOWN
        write("empty.json", "");                                       // 0바이트 제외
    }

    @Test
    void classifiesByPattern_withSearchClaimingBeforeMiscLog() {
        Map<DomainType, List<Path>> result = scanner.scan(root);

        assertTrue(hasFile(result, DomainType.FOLLOW, "following.json"));
        assertTrue(hasFile(result, DomainType.FOLLOW, "followers_1.json"));
        assertTrue(hasFile(result, DomainType.MESSAGE, "message_1.json"));
        assertTrue(hasFile(result, DomainType.LOGIN, "login_activity.json"));
        assertTrue(hasFile(result, DomainType.SEARCH, "account_searches.json"));   // claim 우선
        assertTrue(hasFile(result, DomainType.MISC_LOG, "some_other_log.json"));
        assertTrue(hasFile(result, DomainType.IMPORT, "personal_information.json"));
        assertTrue(hasFile(result, DomainType.MISC_LOG, "mystery.json"));   // catch-all (전용 도메인 없음)
    }

    @Test
    void excludesMediaAndEmptyFiles() {
        Map<DomainType, List<Path>> result = scanner.scan(root);
        boolean anyMedia = result.values().stream().flatMap(List::stream)
                .anyMatch(p -> p.getFileName().toString().equals("pic.jpg"));
        boolean anyEmpty = result.values().stream().flatMap(List::stream)
                .anyMatch(p -> p.getFileName().toString().equals("empty.json"));
        assertFalse(anyMedia);
        assertFalse(anyEmpty);
    }

    @Test
    void searchFileNotDoubleCountedAsMiscLog() {
        Map<DomainType, List<Path>> result = scanner.scan(root);
        assertFalse(hasFile(result, DomainType.MISC_LOG, "account_searches.json"));
    }

    @Test
    void classifiesRealSearchFileNames_underRecentSearches_asSearchNotMiscLog() throws IOException {
        // 실데이터(2026-06-05): logged_information/recent_searches/ 하위 *_searches.json (history 토큰 없음)
        write("logged_information/recent_searches/word_or_phrase_searches.json", "{}");
        write("logged_information/recent_searches/profile_searches.json", "{}");

        Map<DomainType, List<Path>> result = scanner.scan(root);

        assertTrue(hasFile(result, DomainType.SEARCH, "word_or_phrase_searches.json"));
        assertTrue(hasFile(result, DomainType.SEARCH, "profile_searches.json"));
        assertFalse(hasFile(result, DomainType.MISC_LOG, "word_or_phrase_searches.json"));
    }

    @Test
    void threadsFiles_routedToMiscLog_notFollowOrImport() throws IOException {
        // 실데이터: your_instagram_activity/threads/ 의 following.json·personal_information.json 은
        // Threads 데이터 — 메뉴 도메인 파일명과 겹쳐도 FOLLOW/IMPORT 로 가면 안 된다(exclude-path).
        // catch-all 정책상 데이터 자체는 MISC_LOG('각종 로그')로 노출된다.
        write("your_instagram_activity/threads/following.json", "{}");
        write("your_instagram_activity/threads/personal_information.json", "{}");

        Map<DomainType, List<Path>> result = scanner.scan(root);

        assertFalse(hasPath(result, DomainType.FOLLOW, "/threads/following.json"));
        assertFalse(hasPath(result, DomainType.IMPORT, "/threads/personal_information.json"));
        assertTrue(hasPath(result, DomainType.MISC_LOG, "/threads/following.json"));
        assertTrue(hasPath(result, DomainType.MISC_LOG, "/threads/personal_information.json"));
    }

    @Test
    void postsJson_classifiedActivity_butAdsPostsViewed_routedToMiscLog() throws IOException {
        // 실데이터: media/posts.json(피드 본파일)은 posts_ 접두에 안 걸려 별도 name-equals 로 ACTIVITY 분류.
        // 반면 ads_and_topics/posts_viewed.json(광고 추적)은 posts_ 접두로 ACTIVITY 에 오분류되던 것을
        // exclude-path 로 차단 → catch-all 정책상 MISC_LOG('각종 로그')로 노출.
        write("your_instagram_activity/media/posts.json", "[]");
        write("ads_information/ads_and_topics/posts_viewed.json", "[]");
        write("ads_information/ads_and_topics/posts_you're_not_interested_in.json", "[]");

        Map<DomainType, List<Path>> result = scanner.scan(root);

        assertTrue(hasFile(result, DomainType.ACTIVITY, "posts.json"));
        assertFalse(hasPath(result, DomainType.ACTIVITY, "/ads_and_topics/posts_viewed.json"));
        assertTrue(hasPath(result, DomainType.MISC_LOG, "/ads_and_topics/posts_viewed.json"));
        assertTrue(hasPath(result, DomainType.MISC_LOG, "/ads_and_topics/posts_you're_not_interested_in.json"));
    }

    private boolean hasFile(Map<DomainType, List<Path>> result, DomainType domain, String name) {
        return result.getOrDefault(domain, List.of()).stream()
                .anyMatch(p -> p.getFileName().toString().equals(name));
    }

    private boolean hasPath(Map<DomainType, List<Path>> result, DomainType domain, String pathSubstring) {
        return result.getOrDefault(domain, List.of()).stream()
                .anyMatch(p -> p.toString().replace('\\', '/').contains(pathSubstring));
    }

    private void write(String relative, String content) throws IOException {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, content);
    }
}
