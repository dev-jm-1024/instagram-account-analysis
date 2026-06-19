package com.instagram.analyze.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * config 통합 검증 — @ConfigurationProperties 바인딩 + Jackson non_null 전역(키 부재 == null) 동작.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConfigIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private InstagramProperties properties;

    @Test
    void instagramProperties_bindFromYaml() {
        assertEquals(10, properties.getExplorer().getMaxDepth());
        assertEquals(10L * 1024 * 1024, properties.getExplorer().getMaxFileBytes());
        assertEquals(10, properties.getMessage().getTopN());
    }

    @Test
    void jacksonNonNull_omitsNullKeys_inSuccessEnvelope() throws Exception {
        // 임포트 전 overview → ApiResponse{code:null,...} : non_null 이면 code 키 자체가 없어야 함
        mvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").doesNotExist())          // Jackson 3 브릿지 동작 검증
                .andExpect(jsonPath("$.data.importRequired").value(true));
    }
}
