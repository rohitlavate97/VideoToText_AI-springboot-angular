package com.videototext.common.exception;

import com.videototext.common.dto.ErrorResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void shouldHandleResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Video", "id", "123e4567-e89b-12d3-a456-426614174000");
        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody().isSuccess());
        assertEquals("RESOURCE_NOT_FOUND", response.getBody().getError().getCode());
        assertTrue(response.getBody().getError().getMessage().contains("Video not found"));
    }

    @Test
    void shouldHandleBadRequestException() {
        BadRequestException ex = new BadRequestException("Invalid video duration");
        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VALIDATION_ERROR", response.getBody().getError().getCode());
        assertEquals("Invalid video duration", response.getBody().getError().getMessage());
    }

    @Test
    void shouldHandleUnauthorizedException() {
        UnauthorizedException ex = new UnauthorizedException("Token expired");
        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("AUTHENTICATION_REQUIRED", response.getBody().getError().getCode());
    }

    @Test
    void shouldHandleConflictException() {
        ConflictException ex = new ConflictException("Email already exists");
        ResponseEntity<ErrorResponse> response = handler.handleApiException(ex);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("DUPLICATE_RESOURCE", response.getBody().getError().getCode());
    }

    @Test
    void shouldHandleGenericExceptionWithoutExposingInternalDetails() {
        RuntimeException ex = new RuntimeException("Database connection timeout or internal secret error");
        ResponseEntity<ErrorResponse> response = handler.handleGenericException(ex);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("PROCESSING_ERROR", response.getBody().getError().getCode());
        assertFalse(response.getBody().getError().getMessage().contains("secret"));
    }
}
