package com.instagram.analyze.domain.message;

import lombok.Getter;

/**
 * DM 대화방 단위 통계 (domain.md 3절). 실제 메시지 원문(content)은 보관하지 않는다.
 *
 * <p>hourlyDistribution 은 0~23 시 버킷이며 방어복사하여 노출한다.
 * (요일 축은 보관하지 않으므로 히트맵 요일 집계에는 쓸 수 없다 — 히트맵은 임포트 시점 산출.)
 */
@Getter
public final class Conversation {

    private final String roomId;          // inbox 하위 디렉토리명 (대화방 식별자)
    private final String partnerName;     // 상대방 표시 이름
    private final int totalCount;
    private final int sentCount;          // sender_name == 본인
    private final int receivedCount;
    private final boolean ownerInitiated; // 첫 메시지를 본인이 보냄 (initiator)
    private final int[] hourlyDistribution;

    public Conversation(String roomId, String partnerName, int totalCount, int sentCount,
                        int receivedCount, boolean ownerInitiated, int[] hourlyDistribution) {
        this.roomId = roomId;
        this.partnerName = partnerName;
        this.totalCount = totalCount;
        this.sentCount = sentCount;
        this.receivedCount = receivedCount;
        this.ownerInitiated = ownerInitiated;
        this.hourlyDistribution = hourlyDistribution == null ? new int[24] : hourlyDistribution.clone();
    }

    public int[] getHourlyDistribution() {
        return hourlyDistribution.clone();
    }
}
