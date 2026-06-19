package com.instagram.analyze.api.message;

import java.util.List;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.domain.message.MessageStats;
import com.instagram.analyze.domain.message.dto.MessageStatsResponse;

@Component
public class MessageAssembler {

    public MessageStatsResponse toResponse(MessageStats stats) {
        List<MessageStatsResponse.PartnerStat> partners = stats.getTopPartners().stream()
                .map(this::toPartner)
                .toList();
        return new MessageStatsResponse(
                stats.getTotalRooms(),
                stats.getTotalMessages(),
                partners,
                stats.getHourlyDistribution());
    }

    private MessageStatsResponse.PartnerStat toPartner(Conversation c) {
        return new MessageStatsResponse.PartnerStat(
                c.getPartnerName(),
                c.getTotalCount(),
                c.getSentCount(),
                c.getReceivedCount(),
                c.isOwnerInitiated());
    }
}
