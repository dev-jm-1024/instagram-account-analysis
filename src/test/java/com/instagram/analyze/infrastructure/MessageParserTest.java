package com.instagram.analyze.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.common.vo.Username;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.infrastructure.parse.MessageParser;
import com.instagram.analyze.infrastructure.parse.ParseWarnings;
import com.instagram.analyze.infrastructure.text.DefaultStringNormalizer;

class MessageParserTest {

    @TempDir
    Path root;

    private final MessageParser parser = new MessageParser(new DefaultStringNormalizer());

    private Path m1;
    private Path m2;

    @BeforeEach
    void layout() throws IOException {
        Path room = root.resolve("inbox").resolve("alice_123");
        Files.createDirectories(room);
        // message_1: 본인(Me) 1건 (가장 이른 ts → initiator)
        m1 = room.resolve("message_1.json");
        Files.writeString(m1, """
                {"participants":[{"name":"Me"},{"name":"Alice"}],
                 "messages":[{"sender_name":"Me","timestamp_ms":1700000000000,"content":"hi"}]}""");
        // message_2 (분할): Alice 2건 (더 나중 ts)
        m2 = room.resolve("message_2.json");
        Files.writeString(m2, """
                {"participants":[{"name":"Me"},{"name":"Alice"}],
                 "messages":[
                   {"sender_name":"Alice","timestamp_ms":1700000100000,"content":"yo"},
                   {"sender_name":"Alice","timestamp_ms":1700000200000,"content":"?"}]}""");
    }

    @Test
    void mergesSplitFilesIntoOneRoom_withOwnerDependentStats() {
        ParseWarnings warnings = new ParseWarnings();
        List<EpochMillis> heatmapSink = new ArrayList<>();
        AccountIdentity owner = new AccountIdentity(Username.of("me"), "Me");

        List<Conversation> rooms = parser.parse(List.of(m1, m2), owner, warnings, heatmapSink::add);

        // 분할 두 파일이 한 대화방으로 머지
        assertEquals(1, rooms.size());
        Conversation c = rooms.get(0);
        assertEquals("alice_123", c.getRoomId());      // 부모 디렉토리명
        assertEquals("Alice", c.getPartnerName());      // 참가자 − 본인
        assertEquals(3, c.getTotalCount());             // 1 + 2 합산
        assertEquals(1, c.getSentCount());              // Me
        assertEquals(2, c.getReceivedCount());          // Alice
        assertTrue(c.isOwnerInitiated());               // 최소 ts(170...000) sender = Me
        assertEquals(3, heatmapSink.size());            // 히트맵 싱크에 3건
    }

    @Test
    void emptyRoom_excludedFromResult() throws IOException {
        // 메시지 0건(또는 전부 불량)인 대화방은 통계에서 제외(B2) — totalRooms 정확화
        Path emptyRoom = root.resolve("inbox").resolve("ghost_999");
        Files.createDirectories(emptyRoom);
        Path empty = emptyRoom.resolve("message_1.json");
        Files.writeString(empty, """
                {"participants":[{"name":"Me"},{"name":"Ghost"}],"messages":[]}""");

        ParseWarnings warnings = new ParseWarnings();
        List<Conversation> rooms = parser.parse(List.of(m1, m2, empty), null, warnings, t -> { });

        assertEquals(1, rooms.size());                          // alice_123 만, ghost_999 제외
        assertTrue(rooms.stream().noneMatch(c -> c.getRoomId().equals("ghost_999")));
    }

    @Test
    void ownerUnresolved_defersOwnerDependentFields_butKeepsCounts() {
        ParseWarnings warnings = new ParseWarnings();
        List<Conversation> rooms = parser.parse(List.of(m1, m2), null, warnings, t -> { });

        Conversation c = rooms.get(0);
        assertEquals(3, c.getTotalCount());     // owner-독립 카운트는 유지
        assertEquals(0, c.getSentCount());      // owner-의존은 보류
        assertEquals(0, c.getReceivedCount());
        assertFalse(c.isOwnerInitiated());
    }
}
