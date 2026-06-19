package com.instagram.analyze.api.log;

import java.util.List;

import org.springframework.stereotype.Component;

import com.instagram.analyze.domain.log.LogFile;
import com.instagram.analyze.domain.log.LogRecord;
import com.instagram.analyze.domain.log.dto.MiscLogResponse;

@Component
public class MiscLogAssembler {

    public MiscLogResponse toResponse(List<LogFile> files) {
        List<MiscLogResponse.LogFileView> views = files.stream()
                .map(f -> new MiscLogResponse.LogFileView(
                        f.getFileName(),
                        f.getRecords().stream().map(LogRecord::getFields).toList(),
                        // 프론트 집계(월별 추세·타임라인)용: 이미 파싱·정규화한 epoch 를 행 순서대로 노출(미인식 행 null).
                        f.getRecords().stream()
                                .map(r -> r.getTimestamp() == null ? null : r.getTimestamp().getValue())
                                .toList()))
                .toList();
        return new MiscLogResponse(views);
    }
}
