package com.instagram.analyze.domain.message;

import java.util.List;

import lombok.Getter;

/**
 * DM 전체 통계 (domain.md 3절).
 */
@Getter
public final class MessageStats {

    private final int totalRooms;
    private final long totalMessages;
    private final List<Conversation> topPartners;  // 메시지 수 기준 Top10
    private final int[] hourlyDistribution;         // 전체 0~23 시 버킷

    public MessageStats(int totalRooms, long totalMessages, List<Conversation> topPartners,
                        int[] hourlyDistribution) {
        this.totalRooms = totalRooms;
        this.totalMessages = totalMessages;
        this.topPartners = topPartners == null ? List.of() : List.copyOf(topPartners);
        this.hourlyDistribution = hourlyDistribution == null ? new int[24] : hourlyDistribution.clone();
    }

    public int[] getHourlyDistribution() {
        return hourlyDistribution.clone();
    }
}
