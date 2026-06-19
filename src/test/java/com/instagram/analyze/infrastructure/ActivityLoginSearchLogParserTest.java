package com.instagram.analyze.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.domain.activity.LikeTargetType;
import com.instagram.analyze.domain.activity.PostType;
import com.instagram.analyze.domain.login.LoginEvent;
import com.instagram.analyze.domain.login.LoginEventType;
import com.instagram.analyze.domain.search.SearchEntry;
import com.instagram.analyze.domain.log.LogFile;
import com.instagram.analyze.domain.log.LogRecord;
import com.instagram.analyze.infrastructure.parse.ActivityBundle;
import com.instagram.analyze.infrastructure.parse.ActivityParser;
import com.instagram.analyze.infrastructure.parse.LoginParser;
import com.instagram.analyze.infrastructure.parse.MiscLogParser;
import com.instagram.analyze.infrastructure.parse.ParseWarnings;
import com.instagram.analyze.infrastructure.parse.SearchParser;
import com.instagram.analyze.infrastructure.text.DefaultStringNormalizer;

class ActivityLoginSearchLogParserTest {

    @TempDir
    Path dir;

    private final DefaultStringNormalizer norm = new DefaultStringNormalizer();
    private static final long SEC = 1_700_000_000L;   // 초 단위 → ms 로 정규화 기대

    private Path write(String name, String json) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, json);
        return f;
    }

    @Test
    void activity_distinguishesCreationTimestampVsStringListData() throws IOException {
        Path posts = write("posts_1.json",
                "[{\"creation_timestamp\":" + SEC + ",\"title\":\"caption\",\"media\":[]}]");
        Path likes = write("liked_posts.json",
                "{\"likes_media_likes\":[{\"string_list_data\":[{\"href\":\"h\",\"timestamp\":" + SEC + "}]}]}");
        Path comments = write("post_comments_1.json",
                "[{\"string_map_data\":{\"Comment\":{\"value\":\"nice\"},\"Time\":{\"timestamp\":" + SEC + "}}}]");
        Path saved = write("saved_posts.json",
                "{\"saved_saved_media\":[{\"string_list_data\":[{\"href\":\"h\",\"timestamp\":" + SEC + "}]}]}");

        ParseWarnings w = new ParseWarnings();
        ActivityBundle bundle = new ActivityParser(norm).parse(List.of(posts, likes, comments, saved), w);

        assertEquals(1, bundle.getPosts().size());
        assertEquals(PostType.POST, bundle.getPosts().get(0).getType());
        assertEquals("caption", bundle.getPosts().get(0).getTitle());
        assertEquals(SEC * 1000, bundle.getPosts().get(0).getTimestamp().getValue());   // 초 → ms

        assertEquals(1, bundle.getLikes().size());
        assertEquals(LikeTargetType.POST, bundle.getLikes().get(0).getTarget());
        assertEquals(1, bundle.getComments().size());
        assertEquals("nice", bundle.getComments().get(0).getContent());
        assertEquals(1, bundle.getSavedPosts().size());
    }

    @Test
    void activity_parsesLabelValuesListForm_topLevelTimestamp() throws IOException {
        // 실데이터(2026-06-03): posts.json·liked_posts·saved_posts 는 label_values 리스트형이다.
        //   [{ timestamp(최상위), media:[], label_values:[{label:"URL", value, href}], fbid }]
        // string_list_data·creation_timestamp 가 없어 과거엔 전량 스킵됐다(likes 636·saved 7·posts 141 누락).
        Path posts = write("posts.json",
                "[{\"timestamp\":" + SEC + ",\"media\":[],"
                        + "\"label_values\":[{\"label\":\"\\uce21\\uc815\",\"value\":\"cap\"}],\"fbid\":1}]");
        Path likes = write("liked_posts.json",
                "[{\"timestamp\":" + SEC + ",\"media\":[],"
                        + "\"label_values\":[{\"label\":\"URL\",\"value\":\"u\",\"href\":\"https://x/p/1\"}],\"fbid\":2}]");
        Path saved = write("saved_posts.json",
                "[{\"timestamp\":" + SEC + ",\"media\":[],"
                        + "\"label_values\":[{\"label\":\"URL\",\"href\":\"https://x/p/2\"}],\"fbid\":3}]");

        ParseWarnings w = new ParseWarnings();
        ActivityBundle bundle = new ActivityParser(norm).parse(List.of(posts, likes, saved), w);

        assertEquals(1, bundle.getPosts().size());
        assertEquals(PostType.POST, bundle.getPosts().get(0).getType());
        assertEquals(SEC * 1000, bundle.getPosts().get(0).getTimestamp().getValue());

        assertEquals(1, bundle.getLikes().size());
        assertEquals(LikeTargetType.POST, bundle.getLikes().get(0).getTarget());
        assertEquals("https://x/p/1", bundle.getLikes().get(0).getHref());   // label_values "URL" href

        assertEquals(1, bundle.getSavedPosts().size());
        assertEquals("https://x/p/2", bundle.getSavedPosts().get(0).getHref());
    }

    @Test
    void login_distinguishesLoginAndLogoutEventTypes() throws IOException {
        Path login = write("login_activity.json",
                "{\"account_history_login_history\":[{\"string_map_data\":{"
                        + "\"Time\":{\"timestamp\":" + SEC + "},"
                        + "\"IP Address\":{\"value\":\"1.2.3.4\"},"
                        + "\"User Agent\":{\"value\":\"UA\"}}}]}");
        Path logout = write("logout_activity.json",
                "{\"account_history_logout_history\":[{\"string_map_data\":{"
                        + "\"Time\":{\"timestamp\":" + SEC + "}}}]}");

        ParseWarnings w = new ParseWarnings();
        List<LoginEvent> events = new LoginParser(norm).parse(List.of(login, logout), w);

        assertEquals(2, events.size());
        assertTrue(events.stream().anyMatch(e -> e.getType() == LoginEventType.LOGIN && "1.2.3.4".equals(e.getIpAddress())));
        assertTrue(events.stream().anyMatch(e -> e.getType() == LoginEventType.LOGOUT));
    }

    @Test
    void search_keepsKeywordEvenWithoutTimestamp() throws IOException {
        Path search = write("account_searches.json",
                "{\"searches_keyword\":[{\"string_map_data\":{\"Search\":{\"value\":\"cats\"}}}]}");

        ParseWarnings w = new ParseWarnings();
        List<SearchEntry> entries = new SearchParser(norm).parse(List.of(search), w);

        assertEquals(1, entries.size());
        assertEquals("cats", entries.get(0).getKeyword().getValue());
        assertNull(entries.get(0).getTimestamp());   // ts 없어도 검색어 유지
    }

    @Test
    void login_parsesIsoTitleTimestamp_andFindsIpInLocalizedMap() throws IOException {
        // 실데이터(2026-06-05): timestamp 는 title 에 ISO-8601, IP 는 지역화(mojibake) 키 아래.
        // "Time"/"IP Address" 키가 없으므로 title→epoch + 값에서 IP 패턴 탐색이 필요하다.
        Path login = write("login_activity.json",
                "{\"account_history_login_history\":[{"
                        + "\"title\":\"2026-06-02T14:33:20+00:00\","
                        + "\"string_map_data\":{\"\\uc544\\uc774\\ud53c\":{\"value\":\"10.0.0.1\"}}}]}");

        ParseWarnings w = new ParseWarnings();
        List<LoginEvent> events = new LoginParser(norm).parse(List.of(login), w);

        assertEquals(1, events.size());
        assertEquals(LoginEventType.LOGIN, events.get(0).getType());
        assertEquals("10.0.0.1", events.get(0).getIpAddress());
        assertEquals(1780410800L * 1000, events.get(0).getTimestamp().getValue());   // ISO → epoch ms
    }

    @Test
    void search_usesTitleAsKeyword_whenStringListDataHasNoValue() throws IOException {
        // 실데이터: profile_searches.json 은 검색어(username)를 title 에 둔다(value 없음).
        Path search = write("profile_searches.json",
                "{\"searches_user\":[{\"title\":\"test_search1\","
                        + "\"string_list_data\":[{\"href\":\"h\",\"timestamp\":1779338352}]}]}");

        ParseWarnings w = new ParseWarnings();
        List<SearchEntry> entries = new SearchParser(norm).parse(List.of(search), w);

        assertEquals(1, entries.size());
        assertEquals("test_search1", entries.get(0).getKeyword().getValue());
    }

    @Test
    void miscLog_genericKeyValue_groupedByFile() throws IOException {
        Path log = write("some_log.json",
                "{\"whatever\":[{\"string_map_data\":{"
                        + "\"Device\":{\"value\":\"pixel\"},"
                        + "\"Time\":{\"timestamp\":" + SEC + "}}}]}");

        ParseWarnings w = new ParseWarnings();
        List<LogFile> files = new MiscLogParser(norm).parse(List.of(log), w);

        assertEquals(1, files.size());
        assertEquals("some_log.json", files.get(0).getFileName());
        assertEquals(1, files.get(0).getRecords().size());
        assertEquals("pixel", files.get(0).getRecords().get(0).getFields().get("Device"));
        assertEquals(SEC * 1000, files.get(0).getRecords().get(0).getTimestamp().getValue());
    }

    @Test
    void miscLog_normalizesMojibakeFieldKeys() throws IOException {
        // 실데이터(profile_changes 등): string_map_data 키가 latin1-misencoded 한글("변경됨")이다.
        // 값뿐 아니라 키도 정규화해야 각종 로그에서 필드명이 안 깨진다.
        // "ë³ê²½ë¨" = "변경됨" 의 mojibake.
        Path log = write("profile_changes.json",
                "[{\"string_map_data\":{"
                        + "\"\\u00eb\\u00b3\\u0080\\u00ea\\u00b2\\u00bd\\u00eb\\u0090\\u00a8\":{\"value\":\"bio\"}}}]");

        ParseWarnings w = new ParseWarnings();
        List<LogFile> files = new MiscLogParser(norm).parse(List.of(log), w);

        assertEquals("bio", files.get(0).getRecords().get(0).getFields().get("변경됨"));   // 키 mojibake 복구
    }

    @Test
    void miscLog_catchAll_flattensLabelValues_andHandlesObjectOnlyFile() throws IOException {
        // catch-all: 전용 메뉴 없는 파일(광고·조회 등 label_values 리스트형, 그리고 배열 없는 단일 객체)도
        // '각종 로그'에 표로 보여야 한다.
        Path adsViewed = write("ads_viewed.json",
                "[{\"timestamp\":" + SEC + ",\"media\":[],\"label_values\":["
                        + "{\"label\":\"Author\",\"value\":\"acme\"},"
                        + "{\"label\":\"URL\",\"href\":\"https://x/ad/1\"}],\"fbid\":9}]");
        Path settings = write("some_settings.json",
                "{\"enabled\":true,\"region\":\"KR\"}");   // 배열 없는 단일 객체

        ParseWarnings w = new ParseWarnings();
        List<LogFile> files = new MiscLogParser(norm).parse(List.of(adsViewed, settings), w);

        LogFile ads = files.stream().filter(f -> f.getFileName().equals("ads_viewed.json")).findFirst().orElseThrow();
        assertEquals(1, ads.getRecords().size());
        assertEquals("acme", ads.getRecords().get(0).getFields().get("Author"));        // label_values 평면화
        assertEquals("https://x/ad/1", ads.getRecords().get(0).getFields().get("URL")); // value 없으면 href
        assertEquals(SEC * 1000, ads.getRecords().get(0).getTimestamp().getValue());     // 최상위 timestamp

        LogFile set = files.stream().filter(f -> f.getFileName().equals("some_settings.json")).findFirst().orElseThrow();
        assertEquals(1, set.getRecords().size());   // 단일 객체 = 1레코드
        assertEquals("KR", set.getRecords().get(0).getFields().get("region"));
    }

    @Test
    void miscLog_liftsNestedMediaUriCaptionAndTimestamp() throws IOException {
        // 실데이터(archived_posts 등): 캡션(title)·미디어 경로(uri)·시각(creation_timestamp)이 전부
        // media[] 안에 중첩 → 기존 3패스로는 빈 행. 첫 미디어에서 끌어올려야 한다.
        Path archived = write("archived_posts.json",
                "{\"ig_archived_post_media\":[{\"media\":[{"
                        + "\"uri\":\"media/archived_posts/abc.jpg\","
                        + "\"creation_timestamp\":" + SEC + ","
                        + "\"title\":\"my caption\"}]}]}");

        ParseWarnings w = new ParseWarnings();
        List<LogFile> files = new MiscLogParser(norm).parse(List.of(archived), w);

        LogRecord rec = files.get(0).getRecords().get(0);
        assertEquals("media/archived_posts/abc.jpg", rec.getFields().get("uri"));   // 미디어 경로
        assertEquals("my caption", rec.getFields().get("title"));                   // 캡션
        assertEquals(SEC * 1000, rec.getTimestamp().getValue());                    // 중첩 creation_timestamp
    }

    @Test
    void miscLog_liftsTimestampFromStringListData() throws IOException {
        // following_hashtags: title 은 최상위지만 시각은 string_list_data 안에만 있다.
        Path hashtags = write("following_hashtags.json",
                "{\"relationships_following_hashtags\":[{"
                        + "\"title\":\"#seoul\","
                        + "\"string_list_data\":[{\"href\":\"https://x/tag\",\"value\":\"\",\"timestamp\":" + SEC + "}]}]}");

        ParseWarnings w = new ParseWarnings();
        List<LogFile> files = new MiscLogParser(norm).parse(List.of(hashtags), w);

        LogRecord rec = files.get(0).getRecords().get(0);
        assertEquals("#seoul", rec.getFields().get("title"));
        assertEquals(SEC * 1000, rec.getTimestamp().getValue());   // string_list_data 시각 보강
    }
}
