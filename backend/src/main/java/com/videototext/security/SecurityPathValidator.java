package com.videototext.security;

import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Slf4j
@Component
public class SecurityPathValidator {

    /**
     * Sanitizes an uploaded filename against path traversal, null bytes, and malicious control characters.
     */
    public String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.trim().isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Filename cannot be empty");
        }

        // 1. Detect null byte injection
        if (rawFilename.contains("\0") || rawFilename.contains("%00")) {
            log.warn("Null byte detected in filename: {}", rawFilename);
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Filename contains forbidden null byte character");
        }

        // 2. Detect path traversal attempts
        if (rawFilename.contains("..") || rawFilename.contains("/") || rawFilename.contains("\\")) {
            log.warn("Path traversal sequence detected in filename: {}", rawFilename);
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Filename cannot contain directory navigation or path separators");
        }

        // 3. Extract basename
        String basename = new File(rawFilename).getName().trim();

        // 4. Strip all characters except alphanumeric, dot, dash, and underscore
        String sanitized = basename.replaceAll("[^a-zA-Z0-9._-]", "_");

        // 5. Guard against leading/trailing dots or dashes
        while (sanitized.startsWith(".")) {
            sanitized = sanitized.substring(1);
        }

        if (sanitized.isEmpty() || sanitized.length() > 255) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Sanitized filename is invalid or exceeds 255 characters");
        }

        return sanitized;
    }

    /**
     * Validates that an S3 or storage object key is safe and contained within allowed prefixes.
     */
    public void validateStorageKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Storage key cannot be empty");
        }

        if (key.contains("..") || key.contains("\0") || key.contains("%00") || key.startsWith("/")) {
            log.warn("Malicious storage key detected: {}", key);
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Invalid storage key path");
        }

        if (!key.startsWith("videos/") && !key.startsWith("subtitles/") && !key.startsWith("exports/")) {
            log.warn("Storage key escapes allowed root namespace: {}", key);
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Storage key must reside within an authorized namespace");
        }
    }

    /**
     * Validates that a resolved local file path does not escape the configured base directory.
     */
    public void validatePathContainment(File targetFile, File baseDirectory) {
        try {
            Path targetPath = targetFile.getCanonicalFile().toPath();
            Path basePath = baseDirectory.getCanonicalFile().toPath();

            if (!targetPath.startsWith(basePath)) {
                log.error("Path traversal detected! Target {} escapes base directory {}", targetPath, basePath);
                throw new BadRequestException(ErrorCode.ACCESS_DENIED, "Access denied: Path escapes root directory");
            }
        } catch (IOException e) {
            throw new BadRequestException(ErrorCode.PROCESSING_ERROR, "Failed to resolve canonical path: " + e.getMessage());
        }
    }
}
