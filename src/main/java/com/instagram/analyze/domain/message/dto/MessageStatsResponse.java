package com.instagram.analyze.domain.message.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * {@code GET /api/messages/stats} 응답: 대화방 수·Top10·시간대 분포.
 */
@Getter
public class MessageStatsResponse {

    private final int totalRooms;
    private final long totalMessages;
    private final List<PartnerStat> topPartners;
    private final int[] hourlyDistribution;

    public MessageStatsResponse(int totalRooms, long totalMessages, List<PartnerStat> topPartners,
                                int[] hourlyDistribution) {
        this.totalRooms = totalRooms;
        this.totalMessages = totalMessages;
        this.topPartners = topPartners == null ? List.of() : List.copyOf(topPartners);
        this.hourlyDistribution = hourlyDistribution == null ? new int[24] : hourlyDistribution.clone();
    }

    public int[] getHourlyDistribution() {
        return hourlyDistribution.clone();
    }

    @Getter
    @AllArgsConstructor
    public static class PartnerStat {
        private final String partnerName;
        private final int messageCount;
        private final int sentCount;
        private final int receivedCount;
        private final boolean ownerInitiated;
    }
}
