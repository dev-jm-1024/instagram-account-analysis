package com.instagram.analyze.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 업로드·candidates 엔드포인트 MVC 통합 — 동기 실행기로 추출까지 결정적. data.root 는 임시 디렉토리로 override.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
class ImportUploadControllerTest {

    @TempDir
    static Path dataRoot;

    @DynamicPropertySource
    static void dataRootProperty(DynamicPropertyRegistry registry) {
        registry.add("instagram.data.root", () -> dataRoot.toString());
    }

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean
        Executor importExecutor() {
            return Runnable::run;   // 업로드 추출 즉시 실행 → 응답 시 COMPLETED
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void uploadZip_thenDiscoveredInCandidates() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "instagram-me-2026-06-01-abcd.zip", "application/zip", zipWithFollowing());

        mvc.perform(multipart("/api/import/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))   // 동기 실행기
                .andExpect(jsonPath("$.extractedEntries").value(1));

        mvc.perform(get("/api/import/candidates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates[0].name").value("instagram-me-2026-06-01-abcd"))
                .andExpect(jsonPath("$.candidates[0].exportedAt").value("2026-06-01"));
    }

    @Test
    void upload_blankFileName_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "", "application/zip", new byte[] {1, 2});

        mvc.perform(multipart("/api/import/upload").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private byte[] zipWithFollowing() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("connections/followers_and_following/following.json"));
            zos.write("{\"relationships_following\":[]}".getBytes());
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
