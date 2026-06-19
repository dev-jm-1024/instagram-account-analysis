package com.instagram.analyze.api.follow;

import java.util.List;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.follow.FollowAnalysis;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.follow.dto.FollowResponse;

@Component
public class FollowAssembler {

    public FollowResponse toResponse(FollowAnalysis analysis, List<FollowEntry> items) {
        List<FollowResponse.FollowItem> dto = items.stream()
                .map(e -> new FollowResponse.FollowItem(
                        e.getUsername().getValue(),
                        e.getTimestamp() == null ? null : e.getTimestamp().getValue()))
                .toList();
        return new FollowResponse(
                analysis.getFollowerCount(),
                analysis.getFollowingCount(),
                analysis.getMutual().size(),
                analysis.getIFollowOnly().size(),
                analysis.getFollowsMeOnly().size(),
                dto);
    }
}
