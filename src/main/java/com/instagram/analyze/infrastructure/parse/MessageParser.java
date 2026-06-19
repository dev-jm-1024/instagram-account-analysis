package com.instagram.analyze.infrastructure.parse;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;

import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.domain.common.vo.AccountIdentity;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.infrastructure.text.StringNormalizer;

/**
 * DM 파서 (domain.md 3절). 원문(content)은 보관하지 않고 통계만 집계한다.
 *
 * <ul>
 *   <li>roomId = 파일의 <b>부모 디렉토리명</b>(message_*.json 의 상위 {상대방} 폴더)</li>
 *   <li>분할 파일(message_1/2)은 같은 roomId 로 묶어 한 {@link Conversation} 으로 합산</li>
 *   <li>owner-독립: totalCount·hourlyDistribution[24]·participants. owner-의존(sent/received/initiator)은
 *       owner 가 있을 때만 채우고, 없으면 0/false 로 보류(resolveOwner 재파싱 시 채움)</li>
 *   <li>initiator = 배열 순서 무관, <b>최소 timestamp 메시지의 sender</b></li>
 *   <li>DM raw timestamp 는 Conversation 에 남지 않으므로 히트맵용으로 {@code onTimestamp} 싱크에 흘린다</li>
 * </ul>
 */
@Component
public class MessageParser extends AbstractJsonParser {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    public MessageParser(StringNormalizer normalizer) {
        super(normalizer);
    }

    /**
     * @param owner       본인(nullable). null 이면 owner-의존 필드는 보류.
     * @param onTimestamp 유효 메시지 timestamp 싱크(히트맵 집계용). 미사용 시 {@code t -> {}}.
     */
    public List<Conversation> parse(List<Path> messageFiles, AccountIdentity owner,
                                    ParseWarnings warnings, Consumer<EpochMillis> onTimestamp) {
        Map<String, RoomAccumulator> rooms = new LinkedHashMap<>();
        String ownerName = owner != null ? owner.getDisplayName() : null;

        for (Path file : messageFiles) {
            String roomId = file.getParent() != null
                    ? file.getParent().getFileName().toString() : "unknown";
            RoomAccumulator room = rooms.computeIfAbsent(roomId, RoomAccumulator::new);
            parseFile(file, room, ownerName, warnings, onTimestamp);
        }

        List<Conversation> result = new ArrayList<>();
        for (RoomAccumulator room : rooms.values()) {
            if (room.total == 0) {
                continue;   // B2: 빈 대화방(유효 메시지 0) 제외 — totalRooms·conversationCount 정확화
            }
            result.add(room.toConversation(ownerName));
        }
        return result;
    }

    /**
     * 본인 식별 fallback (domain.md 1.4 ②): 모든 대화방 participants 의 교집합 = 본인.
     * 단일 방이면 교집합이 2명이라 모호 → empty(personal_information/수동으로 위임).
     * 교집합이 정확히 1명일 때만 자동 확정한다.
     */
    public Optional<String> resolveOwnerByParticipants(List<Path> messageFiles, ParseWarnings warnings) {
        Map<String, Set<String>> roomParticipants = new LinkedHashMap<>();
        for (Path file : messageFiles) {
            String roomId = file.getParent() != null
                    ? file.getParent().getFileName().toString() : "unknown";
            readParticipants(file, roomParticipants.computeIfAbsent(roomId, k -> new LinkedHashSet<>()), warnings);
        }
        if (roomParticipants.size() < 2) {
            return Optional.empty();   // 단일 방 모호
        }
        Iterator<Set<String>> it = roomParticipants.values().iterator();
        Set<String> intersection = new LinkedHashSet<>(it.next());
        while (it.hasNext()) {
            intersection.retainAll(it.next());
        }
        return intersection.size() == 1 ? Optional.of(intersection.iterator().next()) : Optional.empty();
    }

    private void readParticipants(Path file, Set<String> into, ParseWarnings warnings) {
        try (JsonParser parser = mapper.createParser(file)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return;
            }
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String key = parser.currentName();
                parser.nextToken();
                if (key.equals("participants") && parser.currentToken() == JsonToken.START_ARRAY) {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        JsonNode participant = parser.readValueAsTree();
                        String name = text(participant.get("name"));
                        if (name != null) {
                            into.add(name);
                        }
                    }
                } else {
                    parser.skipChildren();
                }
            }
        } catch (RuntimeException e) {
            warnings.add(ParseWarningCode.JSON_ERROR, file.toString(), e.getMessage());
        }
    }

    private void parseFile(Path file, RoomAccumulator room, String ownerName,
                           ParseWarnings warnings, Consumer<EpochMillis> onTimestamp) {
        try (JsonParser parser = mapper.createParser(file)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                warnings.add(ParseWarningCode.SCHEMA_MISMATCH, file.toString(), "root is not object");
                return;
            }
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                String key = parser.currentName();
                parser.nextToken();
                if (key.equals("participants") && parser.currentToken() == JsonToken.START_ARRAY) {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        JsonNode participant = parser.readValueAsTree();
                        String name = text(participant.get("name"));
                        if (name != null) {
                            room.participants.add(name);
                        }
                    }
                } else if (key.equals("messages") && parser.currentToken() == JsonToken.START_ARRAY) {
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        handleMessage(parser.readValueAsTree(), room, ownerName, file, warnings, onTimestamp);
                    }
                } else {
                    parser.skipChildren();
                }
            }
        } catch (RuntimeException e) {
            warnings.add(ParseWarningCode.JSON_ERROR, file.toString(), e.getMessage());
        }
    }

    private void handleMessage(JsonNode message, RoomAccumulator room, String ownerName,
                               Path file, ParseWarnings warnings, Consumer<EpochMillis> onTimestamp) {
        JsonNode tsNode = message.get("timestamp_ms");
        if (tsNode == null || !tsNode.isNumber() || tsNode.asLong() <= 0) {
            warnings.add(ParseWarningCode.TIMESTAMP_INVALID, file.toString(), null);
            return;
        }
        String sender = text(message.get("sender_name"));
        if (sender == null) {
            warnings.add(ParseWarningCode.VALUE_BLANK, file.toString(), null);
            return;
        }
        EpochMillis ts = EpochMillis.normalize(tsNode.asLong());   // 이미 ms → 그대로
        room.total++;
        room.hourly[ts.hourOfDay(ZONE)]++;
        onTimestamp.accept(ts);
        if (ts.getValue() < room.initiatorTs) {
            room.initiatorTs = ts.getValue();
            room.initiatorSender = sender;
        }
        if (ownerName != null) {
            if (sender.equals(ownerName)) {
                room.sent++;
            } else {
                room.received++;
            }
        }
    }

    /** 대화방 단위 누적기. */
    private static final class RoomAccumulator {
        private final String roomId;
        private final LinkedHashSet<String> participants = new LinkedHashSet<>();
        private final int[] hourly = new int[24];
        private long total;
        private long sent;
        private long received;
        private long initiatorTs = Long.MAX_VALUE;
        private String initiatorSender;

        RoomAccumulator(String roomId) {
            this.roomId = roomId;
        }

        Conversation toConversation(String ownerName) {
            String partnerName;
            boolean ownerInitiated = false;
            if (ownerName != null) {
                List<String> partners = participants.stream()
                        .filter(name -> !name.equals(ownerName)).toList();
                partnerName = partners.isEmpty() ? String.join(", ", participants) : String.join(", ", partners);
                ownerInitiated = ownerName.equals(initiatorSender);
            } else {
                // owner 미확정 — partnerName 보류(stats() 는 게이트되어 노출 안 됨)
                partnerName = participants.isEmpty() ? roomId : String.join(", ", participants);
            }
            return new Conversation(roomId, partnerName, (int) total, (int) sent, (int) received,
                    ownerInitiated, hourly);
        }
    }
}
