package com.instagram.analyze.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * ImportController 통합 — 실제 RealImportService + 동기 실행기로 결정적 검증.
 */
@SpringBootTest(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ImportControllerTest {

    @TestConfiguration
    static class SyncExecutorConfig {
        @Bean
        Executor importExecutor() {
            return Runnable::run;   // 동기 실행 → importFrom 반환 시 이미 COMPLETED
        }
    }

    @Autowired
    private MockMvc mvc;

    @TempDir
    Path exportDir;

    private String body(String json) {
        return json;
    }

    @Test
    void start_validExport_completesSynchronously() throws Exception {
        Files.writeString(exportDir.resolve("following.json"), "{\"relationships_following\":[]}");
        mvc.perform(post("/api/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderPath\":\"" + exportDir.toString() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void start_blankPath_returns400ImportPathBlank() throws Exception {
        mvc.perform(post("/api/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderPath\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_PATH_BLANK"));
    }

    @Test
    void start_nonExistentPath_returns400PathNotFound() throws Exception {
        mvc.perform(post("/api/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderPath\":\"/no/such/dir_xyz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMPORT_PATH_NOT_FOUND"));
    }

    @Test
    void reset_clearsImportedData_backToIdle() throws Exception {
        Files.writeString(exportDir.resolve("following.json"), "{\"relationships_following\":[]}");
        mvc.perform(post("/api/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"folderPath\":\"" + exportDir.toString() + "\"}"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(delete("/api/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IDLE"));

        mvc.perform(get("/api/import/status"))
                .andExpect(jsonPath("$.status").value("IDLE"));
    }

    @Test
    void resolveOwner_blankUsername_returns400OwnerInputBlank() throws Exception {
        mvc.perform(post("/api/import/owner")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("OWNER_INPUT_BLANK"));
    }
}
