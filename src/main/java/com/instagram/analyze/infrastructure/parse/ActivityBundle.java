package com.instagram.analyze.infrastructure.parse;

import java.util.List;

import com.instagram.analyze.domain.activity.CommentEntry;
import com.instagram.analyze.domain.activity.LikeEntry;
import com.instagram.analyze.domain.activity.Post;
import com.instagram.analyze.domain.activity.SavedPost;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 활동(4) 파싱 산출물 묶음 — 게시물·좋아요·댓글·저장. */
@Getter
@AllArgsConstructor
public class ActivityBundle {
    private final List<Post> posts;
    private final List<LikeEntry> likes;
    private final List<CommentEntry> comments;
    private final List<SavedPost> savedPosts;
}
