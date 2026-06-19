package com.instagram.analyze.application.follow;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.instagram.analyze.application.store.ImportReadStore;
import com.instagram.analyze.application.support.ImportGuard;
import com.instagram.analyze.domain.common.vo.Username;
import com.instagram.analyze.domain.follow.FollowAnalysis;
import com.instagram.analyze.domain.follow.FollowEntry;
import com.instagram.analyze.domain.follow.FollowQueryType;
import com.instagram.analyze.domain.follow.FollowRelationType;

/**
 * {@link FollowService} 구현. store 의 원본 {@code FollowEntry} 에 대해 집계한다(파서 무관).
 */
@Service
public class FollowServiceImpl implements FollowService {

    private final ImportReadStore store;
    private final ImportGuard guard;

    public FollowServiceImpl(ImportReadStore store, ImportGuard guard) {
        this.store = store;
        this.guard = guard;
    }

    @Override
    public FollowAnalysis analyze() {
        guard.requireCompleted();
        List<FollowEntry> following = byRelation(FollowRelationType.FOLLOWING);
        List<FollowEntry> followers = byRelation(FollowRelationType.FOLLOWER);
        Set<Username> followingSet = following.stream().map(FollowEntry::getUsername).collect(Collectors.toSet());
        Set<Username> followerSet = followers.stream().map(FollowEntry::getUsername).collect(Collectors.toSet());

        List<FollowEntry> mutual = following.stream()
                .filter(e -> followerSet.contains(e.getUsername())).toList();
        List<FollowEntry> iFollowOnly = following.stream()
                .filter(e -> !followerSet.contains(e.getUsername())).toList();
        List<FollowEntry> followsMeOnly = followers.stream()
                .filter(e -> !followingSet.contains(e.getUsername())).toList();

        return new FollowAnalysis(followers.size(), following.size(), mutual, iFollowOnly, followsMeOnly);
    }

    @Override
    public List<FollowEntry> findByType(FollowQueryType type) {
        guard.requireCompleted();
        return switch (type) {
            case FOLLOWERS -> byRelation(FollowRelationType.FOLLOWER);
            case FOLLOWING -> byRelation(FollowRelationType.FOLLOWING);
            case MUTUAL -> analyze().getMutual();
            case I_FOLLOW_ONLY -> analyze().getIFollowOnly();
            case FOLLOWS_ME_ONLY -> analyze().getFollowsMeOnly();
            case UNFOLLOWED -> byRelation(FollowRelationType.RECENTLY_UNFOLLOWED);
            case PENDING -> byRelation(FollowRelationType.PENDING_REQUEST);
            case CLOSE_FRIENDS -> byRelation(FollowRelationType.CLOSE_FRIEND);
            case RESTRICTED -> byRelation(FollowRelationType.RESTRICTED);
            case ALL -> store.followEntries();
        };
    }

    private List<FollowEntry> byRelation(FollowRelationType relation) {
        return store.followEntries().stream()
                .filter(e -> e.getRelationType() == relation)
                .toList();
    }
}
