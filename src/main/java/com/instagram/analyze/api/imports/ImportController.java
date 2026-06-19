package com.instagram.analyze.api.imports;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.instagram.analyze.application.imports.ExportDiscoveryService;
import com.instagram.analyze.application.imports.ImportService;
import com.instagram.analyze.application.imports.UploadService;
import com.instagram.analyze.domain.imports.dto.CandidatesResponse;
import com.instagram.analyze.domain.imports.dto.ImportRequest;
import com.instagram.analyze.domain.imports.dto.ImportStatusResponse;
import com.instagram.analyze.domain.imports.dto.OwnerRequest;
import com.instagram.analyze.domain.imports.dto.UploadStatusResponse;

import jakarta.validation.Valid;

/**
 * 임포트 엔드포인트 (부록 A). 모두 localhost 로컬 API.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    private final ImportService importService;
    private final ExportDiscoveryService discoveryService;
    private final UploadService uploadService;
    private final ImportAssembler assembler;

    public ImportController(ImportService importService, ExportDiscoveryService discoveryService,
                            UploadService uploadService, ImportAssembler assembler) {
        this.importService = importService;
        this.discoveryService = discoveryService;
        this.uploadService = uploadService;
        this.assembler = assembler;
    }

    /**
     * POST /api/import/upload — ZIP/JSON 업로드 → data/ 스트리밍 저장 + (ZIP) 비동기 전체 추출.
     * 저장 완료 후 즉시 반환(ZIP=EXTRACTING / 단일=COMPLETED). 추출 진행은 status 폴링.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadStatusResponse upload(@RequestParam("file") MultipartFile file) throws IOException {
        return assembler.toUpload(uploadService.upload(file.getOriginalFilename(), file.getInputStream()));
    }

    /** GET /api/import/upload/status — 업로드·추출 진행 폴링. COMPLETED 후 candidates/import 로 진행. */
    @GetMapping("/upload/status")
    public UploadStatusResponse uploadStatus() {
        return assembler.toUpload(uploadService.status());
    }

    /**
     * GET /api/import/candidates — data/ 에서 탐지된 export 후보 목록(260604_data_scan_plan_v1).
     * 0개=수동 입력 / 1개=자동 임포트 가능 / N개=사용자 선택. 탐지는 실패해도 빈 목록(에러 아님).
     */
    @GetMapping("/candidates")
    public CandidatesResponse candidates() {
        return assembler.toCandidates(
                discoveryService.dataRoot().toString(),
                discoveryService.autoImportSingle(),
                discoveryService.discover());
    }

    /** POST /api/import — ETL 시작(논블로킹). 빈 경로는 @Valid → IMPORT_PATH_BLANK(400). */
    @PostMapping
    public ImportStatusResponse start(@Valid @RequestBody ImportRequest request) {
        return assembler.toStatus(importService.importFrom(request.getFolderPath()));
    }

    /** GET /api/import/status — 진행률·완료 폴링. */
    @GetMapping("/status")
    public ImportStatusResponse status() {
        return assembler.toStatus(importService.status());
    }

    /** DELETE /api/import — 인메모리 데이터 삭제(초기화) → IDLE. 디스크 export 는 유지(재임포트로 복구). */
    @DeleteMapping
    public ImportStatusResponse reset() {
        return assembler.toStatus(importService.reset());
    }

    /** POST /api/import/owner — 자동 식별 실패 시 수동 username. 빈 값은 OWNER_INPUT_BLANK(400). */
    @PostMapping("/owner")
    public ImportStatusResponse resolveOwner(@Valid @RequestBody OwnerRequest request) {
        return assembler.toStatus(importService.resolveOwner(request.getUsername()));
    }
}
