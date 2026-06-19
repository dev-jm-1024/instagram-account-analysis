package com.instagram.analyze.domain.log.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lombok.Getter;

/**
 * {@code GET /api/logs} 응답: 파일별 그룹 키-값 테이블.
 */
@Getter
public class MiscLogResponse {

    private final List<LogFileView> files;

    public MiscLogResponse(List<LogFileView> files) {
        this.files = files == null ? List.of() : List.copyOf(files);
    }

    @Getter
    public static class LogFileView {
        private final String fileName;
        private final List<Map<String, String>> rows;
        /** rows 와 같은 순서·길이로 정렬된 파싱·정규화된 epoch ms. 시각 미인식 행은 null. */
        private final List<Long> timestamps;

        public LogFileView(String fileName, List<Map<String, String>> rows, List<Long> timestamps) {
            this.fileName = fileName;
            this.rows = rows == null ? List.of() : List.copyOf(rows);
            // timestamps 는 null 원소(시각 미인식 행)를 포함하므로 List.copyOf(null 거부) 대신 unmodifiable wrap.
            this.timestamps = timestamps == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(timestamps));
        }
    }
}
