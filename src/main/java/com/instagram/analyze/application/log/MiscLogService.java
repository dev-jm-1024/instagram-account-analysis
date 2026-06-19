package com.instagram.analyze.application.log;

import java.util.List;

import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.log.LogFile;

/**
 * 각종 로그(8) 조회 서비스 (interface_plan §4.8).
 * 검색 기록(6)이 claim 하지 않은 {@code logged_information/} 나머지 파일만 대상.
 */
public interface MiscLogService {

    /**
     * 파일별 그룹 키-값 로그. {@code GET /api/logs}
     *
     * <p>{@code logged_information/} 디렉토리가 없으면 {@code Sourced.absent(빈 리스트)} → Assembler 가
     * {@code MISC_LOG_DIR_NOT_FOUND}(200, G4) code 를 부여한다.
     */
    Sourced<List<LogFile>> logs();
}
