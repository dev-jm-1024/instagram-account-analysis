package com.instagram.analyze.api.error;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import com.instagram.analyze.application.support.ImportNotCompletedException;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unhandledException_mapsToStructured500() {
        ResponseEntity<ErrorResponse> response = handler.handle(new RuntimeException("boom"));
        assertEquals(500, response.getStatusCode().value());
        assertEquals("INTERNAL_ERROR", response.getBody().getCode());
        assertEquals("boom", response.getBody().getDetail());
    }

    @Test
    void specificHandlerStillWins_overFallback() {
        ResponseEntity<ErrorResponse> response = handler.handle(new ImportNotCompletedException());
        assertEquals(503, response.getStatusCode().value());
        assertEquals("IMPORT_NOT_COMPLETED", response.getBody().getCode());
    }
}
