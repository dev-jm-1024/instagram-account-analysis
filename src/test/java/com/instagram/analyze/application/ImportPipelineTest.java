package com.instagram.analyze.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.application.imports.ImportPipeline;
import com.instagram.analyze.application.imports.ImportValidationException;
import com.instagram.analyze.application.store.ImportSnapshot;
import com.instagram.analyze.config.FollowMappingProperties;
import com.instagram.analyze.config.ScanMappingProperties;
import com.instagram.analyze.infrastructure.identity.DefaultAccountIdentityResolver;
import com.instagram.analyze.infrastructure.parse.ActivityParser;
import com.instagram.analyze.infrastructure.parse.FollowParser;
import com.instagram.analyze.infrastructure.parse.LoginParser;
import com.instagram.analyze.infrastructure.parse.MessageParser;
import com.instagram.analyze.infrastructure.parse.MiscLogParser;
import com.instagram.analyze.infrastructure.parse.SearchParser;
import com.instagram.analyze.infrastructure.scan.DefaultFileScanner;
import com.instagram.analyze.infrastructure.scan.ImportValidator;
import com.instagram.analyze.infrastructure.text.DefaultStringNormalizer;

class ImportPipelineTest {

    @TempDir
    Path root;

    private ImportPipeline pipeline() {
        DefaultStringNormalizer norm = new DefaultStringNormalizer();
        return new ImportPipeline(new ImportValidator(),
                new DefaultFileScanner(ScanMappingProperties.fromClasspathYaml()),
                new DefaultAccountIdentityResolver(norm),
                new FollowParser(norm, FollowMappingProperties.fromClasspathYaml()), new MessageParser(norm),
                new ActivityParser(norm), new LoginParser(norm), new SearchParser(norm), new MiscLogParser(norm));
    }

    private void write(String relative, String json) throws IOException {
        Path target = root.resolve(relative);
        Files.createDirectories(target.getParent());
        Files.writeString(target, json);
    }

    @Test
    void runsFullPipeline_resolvesOwnerFromPersonalInfo_andPrecomputesHeatmap() throws IOException {
        write("personal_information/personal_information.json",
                "{\"profile_user\":[{\"string_map_data\":{\"Username\":{\"value\":\"me\"},\"Name\":{\"value\":\"Me\"}}}]}");
        write("connections/followers_and_following/following.json",
                "{\"relationships_following\":[{\"string_list_data\":[{\"value\":\"alice\",\"timestamp\":1700000000}]}]}");
        write("connections/followers_and_following/followers_1.json",
                "{\"relationships_followers\":[{\"string_list_data\":[{\"value\":\"bob\",\"timestamp\":1700000001}]}]}");
        write("your_instagram_activity/media/posts_1.json",
                "[{\"creation_timestamp\":1700000050,\"title\":\"hi\"}]");
        write("your_instagram_activity/messages/inbox/alice_1/message_1.json",
                "{\"participants\":[{\"name\":\"Me\"},{\"name\":\"Alice\"}],"
                        + "\"messages\":[{\"sender_name\":\"Me\",\"timestamp_ms\":1700000060000}]}");
        write("security_and_login_information/login_and_profile_creation/login_activity.json",
                "{\"account_history_login_history\":[{\"string_map_data\":{\"Time\":{\"timestamp\":1700000070}}}]}");

        ImportSnapshot snapshot = pipeline().run(root, 1_700_000_999_000L);

        assertEquals("COMPLETED", snapshot.getImportResult().getStatus().name());
        assertTrue(snapshot.getImportResult().isOwnerResolved());
        assertEquals("Me", snapshot.getImportResult().getOwner().getDisplayName());

        assertEquals(2, snapshot.getFollowEntries().size());     // alice + bob
        assertEquals(1, snapshot.getPosts().size());
        assertEquals(1, snapshot.getConversations().size());
        assertEquals(1, snapshot.getLoginEvents().size());

        // 히트맵 사전계산: post + dm + login = 3 이벤트 → peak 존재
        assertNotNull(snapshot.getHeatmap().getPeak());
        assertEquals(7, snapshot.getHeatmap().getGrid().length);

        // DM owner 확정 상태라 sent 집계됨
        assertEquals(1, snapshot.getConversations().get(0).getSentCount());
    }

    @Test
    void resolvesOwnerByParticipantsIntersection_whenNoPersonalInfo() throws IOException {
        // personal_information 없음 + 서로 다른 상대의 두 방 → 교집합 = "Me"
        write("connections/followers_and_following/following.json",
                "{\"relationships_following\":[{\"string_list_data\":[{\"value\":\"x\",\"timestamp\":1700000000}]}]}");
        write("messages/inbox/alice_1/message_1.json",
                "{\"participants\":[{\"name\":\"Me\"},{\"name\":\"Alice\"}],"
                        + "\"messages\":[{\"sender_name\":\"Alice\",\"timestamp_ms\":1700000060000}]}");
        write("messages/inbox/bob_1/message_1.json",
                "{\"participants\":[{\"name\":\"Me\"},{\"name\":\"Bob\"}],"
                        + "\"messages\":[{\"sender_name\":\"Me\",\"timestamp_ms\":1700000061000}]}");

        ImportSnapshot snapshot = pipeline().run(root, 1_700_000_999_000L);

        assertTrue(snapshot.getImportResult().isOwnerResolved());
        assertEquals("Me", snapshot.getImportResult().getOwner().getDisplayName());
    }

    @Test
    void b3_ownerFromPersonalInfoMatchesNoSender_treatedAsUnresolved() throws IOException {
        // personal_information 가 "Owner"를 주지만 어느 방 sender 도 "Owner"가 아님 + 교집합도 "Owner"(복구 불가)
        write("personal_information/personal_information.json",
                "{\"profile_user\":[{\"string_map_data\":{\"Username\":{\"value\":\"owner_ig\"},\"Name\":{\"value\":\"Owner\"}}}]}");
        write("messages/inbox/a/message_1.json",
                "{\"participants\":[{\"name\":\"Owner\"},{\"name\":\"Alice\"}],"
                        + "\"messages\":[{\"sender_name\":\"Alice\",\"timestamp_ms\":1700000000000}]}");
        write("messages/inbox/b/message_1.json",
                "{\"participants\":[{\"name\":\"Owner\"},{\"name\":\"Bob\"}],"
                        + "\"messages\":[{\"sender_name\":\"Bob\",\"timestamp_ms\":1700000100000}]}");

        ImportSnapshot snapshot = pipeline().run(root, 1_700_000_999_000L);

        assertFalse(snapshot.getImportResult().isOwnerResolved());     // 매칭 0 → 미해결
        assertEquals(2, snapshot.getConversations().size());           // 방은 유지(메시지 있음)
    }

    @Test
    void b3_recoversRealOwnerViaParticipants_whenPersonalInfoNameMismatches() throws IOException {
        // personal_information 이름("owner_ig")은 sender 와 안 맞지만, participants 교집합("RealOwner")이 복구
        write("personal_information/personal_information.json",
                "{\"profile_user\":[{\"string_map_data\":{\"Username\":{\"value\":\"owner_ig\"},\"Name\":{\"value\":\"owner_ig\"}}}]}");
        write("messages/inbox/a/message_1.json",
                "{\"participants\":[{\"name\":\"RealOwner\"},{\"name\":\"Alice\"}],"
                        + "\"messages\":[{\"sender_name\":\"RealOwner\",\"timestamp_ms\":1700000000000},"
                        + "{\"sender_name\":\"Alice\",\"timestamp_ms\":1700000050000}]}");
        write("messages/inbox/b/message_1.json",
                "{\"participants\":[{\"name\":\"RealOwner\"},{\"name\":\"Bob\"}],"
                        + "\"messages\":[{\"sender_name\":\"Bob\",\"timestamp_ms\":1700000100000}]}");

        ImportSnapshot snapshot = pipeline().run(root, 1_700_000_999_000L);

        assertTrue(snapshot.getImportResult().isOwnerResolved());       // participants 로 복구
        assertEquals("RealOwner", snapshot.getImportResult().getOwner().getDisplayName());
        long sent = snapshot.getConversations().stream().mapToLong(c -> c.getSentCount()).sum();
        assertEquals(1, sent);                                          // RealOwner 가 보낸 1건
    }

    @Test
    void emptyDirectory_throwsNotInstagramExport() {
        ImportValidationException ex = assertThrows(ImportValidationException.class,
                () -> pipeline().run(root, 1L));
        assertEquals(ImportValidationException.Reason.NOT_INSTAGRAM_EXPORT, ex.getReason());
    }

    @Test
    void nonExistentPath_throwsPathNotFound() {
        ImportValidationException ex = assertThrows(ImportValidationException.class,
                () -> pipeline().run(root.resolve("nope"), 1L));
        assertEquals(ImportValidationException.Reason.PATH_NOT_FOUND, ex.getReason());
        assertFalse(ex.getMessage().isBlank());
    }
}
