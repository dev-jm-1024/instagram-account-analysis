package com.instagram.analyze.application.message;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.config.InstagramProperties;
import com.instagram.analyze.domain.message.Conversation;
import com.instagram.analyze.domain.message.MessageStats;

/**
 * {@link MessageService} 구현. owner 미해결 시 {@code requireOwnerResolved()} 가 던진다(부분 응답 없음).
 */
@Service
public class MessageServiceImpl implements MessageService {

    private final ImportReadStore store;
    private final ImportGuard guard;
    private final int topN;

    public MessageServiceImpl(ImportReadStore store, ImportGuard guard, InstagramProperties properties) {
        this.store = store;
        this.guard = guard;
        this.topN = properties.getMessage().getTopN();
    }

    @Override
    public MessageStats stats() {
        guard.requireCompleted();
        guard.requireOwnerResolved();   // 미해결 → throw, 부분 응답 없음 (F)

        List<Conversation> convs = store.conversations();
        long totalMessages = convs.stream().mapToLong(Conversation::getTotalCount).sum();

        List<Conversation> top = convs.stream()
                .sorted(Comparator.comparingInt(Conversation::getTotalCount).reversed())
                .limit(topN)
                .toList();

        int[] hourly = new int[24];
        for (Conversation c : convs) {
            int[] dist = c.getHourlyDistribution();
            for (int h = 0; h < 24 && h < dist.length; h++) {
                hourly[h] += dist[h];
            }
        }
        return new MessageStats(convs.size(), totalMessages, top, hourly);
    }
}
