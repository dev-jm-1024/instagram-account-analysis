package com.instagram.analyze.infrastructure.parse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.instagram.analyze.domain.common.ParseWarningCode;
import com.instagram.analyze.domain.common.vo.ParseWarning;

/**
 * 파싱 중 누적되는 경고 수집기 (domain_exception G6). ETL 이 마지막에 {@code ImportResult.warnings} 로 흘린다.
 *
 * <p>DM 등 일부 파싱은 병렬 스트림으로 처리하므로 thread-safe 하게 둔다(단일 사용자라 경합은 드묾).
 */
public final class ParseWarnings {

    private final List<ParseWarning> warnings = Collections.synchronizedList(new ArrayList<>());

    public void add(ParseWarningCode code, String source, String detail) {
        warnings.add(new ParseWarning(code, source, detail));
    }

    public List<ParseWarning> toList() {
        synchronized (warnings) {
            return List.copyOf(warnings);
        }
    }

    public int size() {
        return warnings.size();
    }

    public boolean isEmpty() {
        return warnings.isEmpty();
    }
}
