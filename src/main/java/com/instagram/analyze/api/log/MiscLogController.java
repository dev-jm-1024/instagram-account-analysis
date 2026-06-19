package com.instagram.analyze.api.log;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.api.common.ApiResultCode;
import com.instagram.analyze.application.log.MiscLogService;
import com.instagram.analyze.application.support.Sourced;
import com.instagram.analyze.domain.log.LogFile;
import com.instagram.analyze.domain.log.dto.MiscLogResponse;

/** GET /api/logs (부록 A). 디렉토리 없으면 200 + MISC_LOG_DIR_NOT_FOUND (G4). */
@RestController
@RequestMapping("/api/logs")
public class MiscLogController {

    private final MiscLogService miscLogService;
    private final MiscLogAssembler assembler;

    public MiscLogController(MiscLogService miscLogService, MiscLogAssembler assembler) {
        this.miscLogService = miscLogService;
        this.assembler = assembler;
    }

    @GetMapping
    public ApiResponse<MiscLogResponse> logs() {
        Sourced<List<LogFile>> result = miscLogService.logs();
        MiscLogResponse body = assembler.toResponse(result.getValue());
        return result.isSourceExists()
                ? ApiResponse.ok(body)
                : ApiResponse.of(ApiResultCode.MISC_LOG_DIR_NOT_FOUND, body);
    }
}
