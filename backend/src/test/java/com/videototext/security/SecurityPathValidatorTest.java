package com.videototext.security;

import com.videototext.common.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SecurityPathValidatorTest {

    private SecurityPathValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SecurityPathValidator();
    }

    @Test
    @DisplayName("Should sanitize valid filenames and reject path traversal sequences")
    void shouldSanitizePathTraversalFilenames() {
        assertEquals("sample.mp4", validator.sanitizeFilename("sample.mp4"));
        assertEquals("report_2026.mp4", validator.sanitizeFilename("report 2026.mp4"));
        assertEquals("test___file.mp4", validator.sanitizeFilename("test$!#file.mp4"));

        assertThrows(BadRequestException.class, () -> validator.sanitizeFilename("../../evil.mp4"));
        assertThrows(BadRequestException.class, () -> validator.sanitizeFilename("..\\..\\evil.mp4"));
    }

    @Test
    @DisplayName("Should validate storage key containment and reject path traversal")
    void shouldValidateStorageKeyContainment() {
        assertDoesNotThrow(() -> validator.validateStorageKey("videos/user-123/video-456/sample.mp4"));

        assertThrows(BadRequestException.class, () -> validator.validateStorageKey("videos/../../etc/passwd"));
        assertThrows(BadRequestException.class, () -> validator.validateStorageKey("videos/..\\windows\\system32"));
        assertThrows(BadRequestException.class, () -> validator.validateStorageKey("/etc/passwd"));
    }

    @Test
    @DisplayName("Should validate path containment within allowed base directory")
    void shouldValidatePathContainment() {
        java.io.File baseDir = new java.io.File(System.getProperty("java.io.tmpdir"));
        java.io.File safeFile = new java.io.File(baseDir, "safe-upload.mp4");

        assertDoesNotThrow(() -> validator.validatePathContainment(safeFile, baseDir));

        java.io.File dangerousFile = new java.io.File(baseDir, "../../Windows/System32/cmd.exe");
        assertThrows(BadRequestException.class, () -> validator.validatePathContainment(dangerousFile, baseDir));
    }
}
