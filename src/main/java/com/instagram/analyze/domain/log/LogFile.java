package com.instagram.analyze.domain.log;

import java.util.List;

import lombok.Getter;

/**
 * 파일 단위로 그룹화된 각종 로그 (domain.md 8절). 어느 파일에서 온 로그인지 구분한다.
 */
@Getter
public final class LogFile {

    private final String fileName;
    private final List<LogRecord> records;

    public LogFile(String fileName, List<LogRecord> records) {
        this.fileName = fileName;
        this.records = records == null ? List.of() : List.copyOf(records);
    }
}
