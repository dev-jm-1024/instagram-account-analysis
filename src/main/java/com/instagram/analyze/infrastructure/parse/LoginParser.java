package com.instagram.analyze.infrastructure.parse;

import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.domain.common.vo.EpochMillis;
import com.instagram.analyze.domain.login.LoginEvent;
import com.instagram.analyze.domain.login.LoginEventType;
import com.instagram.analyze.infrastructure.text.StringNormalizer;

/**
 * 로그인(7) 파서 — login_activity* / logout_activity* 로 이벤트 타입 구분 (domain.md 7).
 * timestamp(초) 핵심 → 불량 시 스킵. ip/user_agent 는 문자열 그대로(blank 면 null 허용).
 */
@Component
public class LoginParser extends AbstractJsonParser {

    public LoginParser(StringNormalizer normalizer) {
        super(normalizer);
    }

    public List<LoginEvent> parse(List<Path> files, ParseWarnings warnings) {
        List<LoginEvent> out = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            LoginEventType type = name.startsWith("logout_activity")
                    ? LoginEventType.LOGOUT : LoginEventType.LOGIN;
            streamFirstArray(file, warnings, el -> addEvent(el, type, out, file, warnings));
        }
        return out;
    }

    /** IPv4 패턴 — 지역화·mojibake 키 때문에 키 매칭이 안 될 때 값에서 IP 를 직접 찾는다. */
    private static final Pattern IPV4 = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}\\b");

    private void addEvent(JsonNode el, LoginEventType type, List<LoginEvent> out, Path file, ParseWarnings w) {
        Long ts = mapTimestamp(el, "Time");
        if (ts == null) {
            ts = numberOrNull(el.get("timestamp"));
        }
        if (ts == null) {
            ts = isoTitleToEpochSeconds(el);   // 실데이터: title 이 ISO-8601 문자열(epoch 아님)
        }
        if (ts == null || ts <= 0) {
            w.add(ParseWarningCode.TIMESTAMP_INVALID, file.toString(), null);
            return;
        }
        String ip = mapValue(el, "IP Address");
        if (ip == null) {
            ip = text(el.get("ip_address"));
        }
        if (ip == null) {
            ip = findIpInMap(el);   // 실데이터: string_map_data 키가 지역화(mojibake) → 값에서 IP 패턴 탐색
        }
        String userAgent = mapValue(el, "User Agent");
        if (userAgent == null) {
            userAgent = text(el.get("user_agent"));
        }
        out.add(new LoginEvent(EpochMillis.normalize(ts), type, ip, userAgent));
    }

    /** title 이 ISO-8601(예: 2026-06-02T14:33:20+00:00)이면 epoch 초로 변환, 아니면 null. */
    private Long isoTitleToEpochSeconds(JsonNode el) {
        JsonNode title = el.get("title");
        if (title == null || !title.isString()) {
            return null;
        }
        String value = title.asString().trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toEpochSecond();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** string_map_data 값들 중 IPv4 패턴이 있으면 첫 번째를 반환. */
    private String findIpInMap(JsonNode el) {
        JsonNode smd = el.get("string_map_data");
        if (smd == null || !smd.isObject()) {
            return null;
        }
        for (Map.Entry<String, JsonNode> entry : smd.properties()) {
            JsonNode value = entry.getValue().get("value");
            if (value != null && value.isString()) {
                Matcher m = IPV4.matcher(value.asString());
                if (m.find()) {
                    return m.group();
                }
            }
        }
        return null;
    }
}
