package com.instagram.analyze.api.explorer;

import java.nio.file.Path;
import java.util.Locale;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.analyze.api.common.ApiResponse;
import com.instagram.analyze.application.explorer.ExplorerService;
import com.instagram.analyze.domain.explorer.dto.ExplorerFileResponse;
import com.instagram.analyze.domain.explorer.dto.ExplorerTreeResponse;

/** GET /api/explorer/tree·/file·/media (부록 A). 경로 오류는 G5(400/404) 매핑. */
@RestController
@RequestMapping("/api/explorer")
public class ExplorerController {

    private final ExplorerService explorerService;
    private final ExplorerAssembler assembler;

    public ExplorerController(ExplorerService explorerService, ExplorerAssembler assembler) {
        this.explorerService = explorerService;
        this.assembler = assembler;
    }

    @GetMapping("/tree")
    public ApiResponse<ExplorerTreeResponse> tree() {
        return ApiResponse.ok(assembler.toTree(explorerService.tree()));
    }

    @GetMapping("/file")
    public ApiResponse<ExplorerFileResponse> file(@RequestParam("path") String path) {
        return ApiResponse.ok(assembler.toFile(explorerService.file(path)));
    }

    /**
     * 미디어/바이너리 원본 스트리밍 — 탐색기 이미지/영상 미리보기용({@code <img>/<video>}). 확장자로 Content-Type 추론,
     * inline 표시. 로컬 단일 사용자라 동일 export 의 미디어는 캐시 허용. heic 는 브라우저가 못 그릴 수 있음(프론트가 안내).
     */
    @GetMapping("/media")
    public ResponseEntity<Resource> media(@RequestParam("path") String path) {
        Path file = explorerService.mediaFile(path);
        return ResponseEntity.ok()
                .contentType(contentTypeOf(file))
                .cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(new FileSystemResource(file));
    }

    private MediaType contentTypeOf(Path file) {
        String n = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return MediaType.IMAGE_JPEG;
        if (n.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (n.endsWith(".gif")) return MediaType.IMAGE_GIF;
        if (n.endsWith(".webp")) return MediaType.parseMediaType("image/webp");
        if (n.endsWith(".heic") || n.endsWith(".heif")) return MediaType.parseMediaType("image/heic");
        if (n.endsWith(".mp4")) return MediaType.parseMediaType("video/mp4");
        if (n.endsWith(".mov")) return MediaType.parseMediaType("video/quicktime");
        if (n.endsWith(".webm")) return MediaType.parseMediaType("video/webm");
        return MediaType.APPLICATION_OCTET_STREAM;
    }
}
