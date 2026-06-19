package com.instagram.analyze.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 조회 컨트롤러 통합 — 공통 envelope · G4 · 게이트(503/importRequired). 동기 실행기로 import 결정적 완료.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ReadControllersTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean
        Executor importExecutor() {
            return Runnable::run;
        }
    }

    @Autowired
    private MockMvc mvc;

    @TempDir
    Path exportDir;

    /** following.json 만 있는 최소 유효 export 를 동기 임포트(→ COMPLETED). */
    private void importMinimalExport() throws Exception {
        Files.writeString(exportDir.resolve("following.json"), "{\"relationships_following\":[]}");
        mvc.perform(post("/api/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"folderPath\":\"" + exportDir.toString() + "\"}"));
    }

    @Test
    void heatmap_beforeImport_returns503() throws Exception {
        mvc.perform(get("/api/heatmap"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("IMPORT_NOT_COMPLETED"));
    }

    @Test
    void overview_beforeImport_returns200ImportRequired() throws Exception {
        mvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.importRequired").value(true));
    }

    @Test
    void searches_afterImportWithoutSearchFiles_returnsG4Code() throws Exception {
        importMinimalExport();
        mvc.perform(get("/api/searches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SEARCH_HISTORY_NOT_FOUND"));
    }

    @Test
    void follows_afterImport_returnsOkEnvelope() throws Exception {
        importMinimalExport();
        mvc.perform(get("/api/follows").param("type", "MUTUAL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followerCount").value(0));
    }

    @Test
    void follows_invalidType_returns400() throws Exception {
        mvc.perform(get("/api/follows").param("type", "NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
