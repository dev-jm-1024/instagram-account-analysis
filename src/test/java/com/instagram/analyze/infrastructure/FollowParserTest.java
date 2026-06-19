package com.instagram.analyze.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.config.FollowMappingProperties;
import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.domain.common.vo.ParseWarning;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.follow.FollowRelationType;
import com.instagram.analyze.infrastructure.parse.FollowParser;
import com.instagram.analyze.infrastructure.parse.ParseWarnings;
import com.instagram.analyze.infrastructure.text.DefaultStringNormalizer;

class FollowParserTest {

    @TempDir
    Path dir;

    private final FollowParser parser =
            new FollowParser(new DefaultStringNormalizer(), FollowMappingProperties.fromClasspathYaml());

    private Path following;
    private Path followers1;
    private Path followers2;

    @BeforeEach
    void layout() throws IOException {
        following = write("following.json", """
                {"relationships_following":[
                  {"string_list_data":[{"value":"alice","timestamp":1700000000,"href":"a"}]}
                ]}""");
        // 분할 파일 — 합산되어야 함
        followers1 = write("followers_1.json", """
                {"relationships_followers":[
                  {"string_list_data":[{"value":"bob","timestamp":1700000001}]}
                ]}""");
        followers2 = write("followers_2.json", """
                {"relationships_followers":[
                  {"string_list_data":[{"value":"carol","timestamp":1700000002}]},
                  {"string_list_data":[]}
                ]}""");
    }

    @Test
    void parsesSchema_summingSplitFiles_andNormalizesSecondsToMillis() {
        ParseWarnings warnings = new ParseWarnings();
        List<FollowEntry> entries = parser.parse(List.of(following, followers1, followers2), warnings);

        // alice(FOLLOWING) + bob, carol(FOLLOWER) = 3 (빈 string_list_data 1건은 스킵)
        assertEquals(3, entries.size());

        FollowEntry alice = entries.stream()
                .filter(e -> e.getUsername().getValue().equals("alice")).findFirst().orElseThrow();
        assertEquals(FollowRelationType.FOLLOWING, alice.getRelationType());
        assertEquals(1700000000L * 1000, alice.getTimestamp().getValue());   // 초 → ms

        long followerCount = entries.stream()
                .filter(e -> e.getRelationType() == FollowRelationType.FOLLOWER).count();
        assertEquals(2, followerCount);   // bob + carol 합산
    }

    @Test
    void accumulatesWarningForEmptyStringListData() {
        ParseWarnings warnings = new ParseWarnings();
        parser.parse(List.of(followers2), warnings);

        boolean hasEmpty = warnings.toList().stream()
                .map(ParseWarning::getCode)
                .anyMatch(c -> c == ParseWarningCode.STRING_LIST_EMPTY);
        assertTrue(hasEmpty);
    }

    @Test
    void keepsFollowEntryWhenTimestampMissingOrZero() throws IOException {
        // close_friends 등은 timestamp 가 0/없음 — username 이 1차 키라 드롭하면 안 됨
        Path closeFriends = write("close_friends.json", """
                {"relationships_close_friends":[
                  {"string_list_data":[{"value":"buddy","timestamp":0}]}
                ]}""");
        ParseWarnings warnings = new ParseWarnings();
        List<FollowEntry> entries = parser.parse(List.of(closeFriends), warnings);

        assertEquals(1, entries.size());
        assertEquals("buddy", entries.get(0).getUsername().getValue());
        assertEquals(FollowRelationType.CLOSE_FRIEND, entries.get(0).getRelationType());
        assertNull(entries.get(0).getTimestamp());   // 미상 → null, 항목은 유지
    }

    @Test
    void parsesLabelValuesForm_closeFriends_topLevelArrayAndLocalizedUsernameLabel() throws IOException {
        // 실데이터(2026-06-03): close_friends·blocked 는 string_list_data·title 이 없는 label_values
        // 리스트형(최상위 배열). username 은 "사용자 이름" 라벨 value, URL value 는 비어 있고 ts 는 최상위.
        // 과거엔 전량 STRING_LIST_EMPTY 로 드롭됐다(close_friends 77건 누락).
        Path closeFriends = write("close_friends.json", """
                [{"timestamp":1737301732,"media":[],
                  "label_values":[
                    {"label":"URL","value":""},
                    {"label":"이름","value":"홍길동"},
                    {"label":"사용자 이름","value":"test_user1"}
                  ]}]""");
        ParseWarnings warnings = new ParseWarnings();
        List<FollowEntry> entries = parser.parse(List.of(closeFriends), warnings);

        assertEquals(1, entries.size());
        assertEquals("test_user1", entries.get(0).getUsername().getValue());   // 이름(홍길동) 아닌 username
        assertEquals(FollowRelationType.CLOSE_FRIEND, entries.get(0).getRelationType());
        assertEquals(1737301732L * 1000, entries.get(0).getTimestamp().getValue());   // 최상위 timestamp
        assertTrue(warnings.toList().isEmpty());   // 더이상 드롭 경고 없음
    }

    @Test
    void parsesSingleObjectRoot_recentlyUnfollowed_labelValuesForm() throws IOException {
        // 실데이터(2026-06-03): recently_unfollowed_profiles.json 은 배열·root-key 없이 단일 객체 1건이
        // 바로 루트에 온다(label_values 폼). 과거엔 SCHEMA_MISMATCH 로 통째로 누락 → 최근 언팔 0.
        Path unfollowed = write("recently_unfollowed_profiles.json", """
                {"timestamp":1778648500,"media":[],
                 "label_values":[
                   {"label":"URL","value":"https://example.com/blog"},
                   {"label":"이름","value":"김철수"},
                   {"label":"사용자 이름","value":"test_user2"}
                 ],"fbid":"10000000000000001"}""");
        ParseWarnings warnings = new ParseWarnings();
        List<FollowEntry> entries = parser.parse(List.of(unfollowed), warnings);

        assertEquals(1, entries.size());
        assertEquals("test_user2", entries.get(0).getUsername().getValue());
        assertEquals(FollowRelationType.RECENTLY_UNFOLLOWED, entries.get(0).getRelationType());
        assertEquals(1778648500L * 1000, entries.get(0).getTimestamp().getValue());
        assertTrue(warnings.toList().isEmpty());   // SCHEMA_MISMATCH 더이상 없음
    }

    @Test
    void usesTitleAsUsername_whenStringListDataHasNoValue() throws IOException {
        // 실데이터 변형(2026-06-05): following.json 은 username 을 title 에 두고
        // string_list_data[0] 엔 href·timestamp 만 있다(value 없음). title 폴백 필요.
        Path realFollowing = write("following.json", """
                {"relationships_following":[
                  {"title":"test_user3",
                   "string_list_data":[{"href":"https://instagram.com/_u/test_user3","timestamp":1779016554}]}
                ]}""");
        ParseWarnings warnings = new ParseWarnings();
        List<FollowEntry> entries = parser.parse(List.of(realFollowing), warnings);

        assertEquals(1, entries.size());
        assertEquals("test_user3", entries.get(0).getUsername().getValue());
        assertEquals(FollowRelationType.FOLLOWING, entries.get(0).getRelationType());
        assertEquals(1779016554L * 1000, entries.get(0).getTimestamp().getValue());   // string_list_data 의 ts 유지
    }

    private Path write(String name, String json) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, json);
        return file;
    }
}
