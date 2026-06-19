package com.instagram.analyze.infrastructure.parse;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import com.instagram.analyze.domain.activity.CommentEntry;
import com.instagram.analyze.domain.activity.CommentSource;
import com.instagram.analyze.domain.activity.LikeEntry;
import com.instagram.analyze.domain.activity.LikeTargetType;
import com.instagram.analyze.domain.activity.Post;
import com.instagram.analyze.domain.activity.PostType;
import com.instagram.analyze.domain.activity.SavedPost;
import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.infrastructure.text.StringNormalizer;

/**
 * 활동(4) 파서 — 파일명으로 게시물/좋아요/댓글/저장을 구분한다 (domain.md 4.1~4.4).
 * timestamp 는 데이터 핵심이라 불량 시 스킵(TIMESTAMP_INVALID). 모두 초 단위 → epoch ms.
 */
@Component
public class ActivityParser extends AbstractJsonParser {

    public ActivityParser(StringNormalizer normalizer) {
        super(normalizer);
    }

    public ActivityBundle parse(List<Path> files, ParseWarnings warnings) {
        List<Post> posts = new ArrayList<>();
        List<LikeEntry> likes = new ArrayList<>();
        List<CommentEntry> comments = new ArrayList<>();
        List<SavedPost> saved = new ArrayList<>();

        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.startsWith("posts_") || name.equals("posts.json")) {
                streamFirstArray(file, warnings, el -> addPost(el, PostType.POST, posts, file, warnings));
            } else if (name.equals("stories.json")) {
                streamFirstArray(file, warnings, el -> addPost(el, PostType.STORY, posts, file, warnings));
            } else if (name.equals("reels.json")) {
                streamFirstArray(file, warnings, el -> addPost(el, PostType.REELS, posts, file, warnings));
            } else if (name.equals("liked_posts.json")) {
                streamFirstArray(file, warnings, el -> addLike(el, LikeTargetType.POST, likes, file, warnings));
            } else if (name.equals("liked_comments.json")) {
                streamFirstArray(file, warnings, el -> addLike(el, LikeTargetType.COMMENT, likes, file, warnings));
            } else if (name.startsWith("post_comments_")) {
                streamFirstArray(file, warnings, el -> addComment(el, CommentSource.POST, comments, file, warnings));
            } else if (name.equals("reels_comments.json")) {
                streamFirstArray(file, warnings, el -> addComment(el, CommentSource.REELS, comments, file, warnings));
            } else if (name.equals("saved_posts.json")) {
                streamFirstArray(file, warnings, el -> addSaved(el, saved, file, warnings));
            }
        }
        return new ActivityBundle(posts, likes, comments, saved);
    }

    private void addPost(JsonNode el, PostType type, List<Post> out, Path file, ParseWarnings w) {
        Long ts = numberOrNull(el.get("creation_timestamp"));
        if (ts == null) {   // media[0].creation_timestamp 폴백
            JsonNode media = el.get("media");
            if (media != null && media.isArray() && !media.isEmpty()) {
                ts = numberOrNull(media.get(0).get("creation_timestamp"));
            }
        }
        if (ts == null) {   // 최상위 timestamp 폴백 (posts.json 의 label_values 리스트형)
            ts = numberOrNull(el.get("timestamp"));
        }
        if (ts == null || ts <= 0) {
            w.add(ParseWarningCode.TIMESTAMP_INVALID, file.toString(), null);
            return;
        }
        out.add(new Post(EpochMillis.normalize(ts), type, text(el.get("title"))));
    }

    private void addLike(JsonNode el, LikeTargetType target, List<LikeEntry> out, Path file, ParseWarnings w) {
        // liked_comments 는 string_list_data 형식, liked_posts 는 label_values 리스트형(최상위 timestamp).
        JsonNode first = firstStringListData(el);
        Long ts;
        String href;
        if (first != null) {
            ts = numberOrNull(first.get("timestamp"));
            href = text(first.get("href"));
        } else {
            ts = numberOrNull(el.get("timestamp"));
            href = labelValue(el, "URL");
        }
        if (ts == null || ts <= 0) {
            w.add(ParseWarningCode.TIMESTAMP_INVALID, file.toString(), null);
            return;
        }
        out.add(new LikeEntry(EpochMillis.normalize(ts), target, href));
    }

    private void addSaved(JsonNode el, List<SavedPost> out, Path file, ParseWarnings w) {
        // 실데이터 saved_posts 는 label_values 리스트형(최상위 timestamp). string_list_data 형식도 허용.
        JsonNode first = firstStringListData(el);
        Long ts;
        String href;
        if (first != null) {
            ts = numberOrNull(first.get("timestamp"));
            href = text(first.get("href"));
        } else {
            ts = numberOrNull(el.get("timestamp"));
            href = labelValue(el, "URL");
        }
        if (ts == null || ts <= 0) {
            w.add(ParseWarningCode.TIMESTAMP_INVALID, file.toString(), null);
            return;
        }
        out.add(new SavedPost(EpochMillis.normalize(ts), href));
    }

    private void addComment(JsonNode el, CommentSource source, List<CommentEntry> out, Path file, ParseWarnings w) {
        Long ts = numberOrNull(el.get("timestamp"));
        if (ts == null) {
            ts = mapTimestamp(el, "Time");
        }
        if (ts == null || ts <= 0) {
            w.add(ParseWarningCode.TIMESTAMP_INVALID, file.toString(), null);
            return;
        }
        out.add(new CommentEntry(EpochMillis.normalize(ts), source, mapValue(el, "Comment")));
    }
}
