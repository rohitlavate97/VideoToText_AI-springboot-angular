package com.videototext.video.validation;

import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class VideoValidator {

    @Value("${app.video.max-duration-minutes:240}")
    private int maxDurationMinutes;

    @Value("${app.video.max-file-size-bytes:10737418240}") // 10 GB
    private long maxFileSizeBytes;

    @Value("${app.video.allowed-extensions:mp4,mkv,avi,mov,webm,flv,wmv,mpeg,3gp}")
    private String allowedExtensionsStr;

    public void validateExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            throw new BadRequestException(ErrorCode.UNSUPPORTED_FORMAT, "Filename must include a valid video extension");
        }

        String extension = filename.substring(filename.lastIndexOf(".") + 1).toLowerCase(Locale.ROOT);
        List<String> allowed = Arrays.stream(allowedExtensionsStr.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        if (!allowed.contains(extension)) {
            throw new BadRequestException(
                    ErrorCode.UNSUPPORTED_FORMAT,
                    String.format("Unsupported video format '.%s'. Allowed formats: %s", extension, allowedExtensionsStr)
            );
        }
    }

    public void validateFileSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Video file cannot be empty");
        }
        if (sizeBytes > maxFileSizeBytes) {
            throw new BadRequestException(
                    ErrorCode.FILE_TOO_LARGE,
                    String.format("File size (%d bytes) exceeds the maximum allowed limit of %d bytes", sizeBytes, maxFileSizeBytes)
            );
        }
    }

    public void validateDuration(int durationSeconds) {
        int maxDurationSeconds = maxDurationMinutes * 60;
        if (durationSeconds <= 0) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Video duration must be greater than zero");
        }
        if (durationSeconds > maxDurationSeconds) {
            throw new BadRequestException(
                    ErrorCode.DURATION_EXCEEDED,
                    String.format("Video duration of %d minutes exceeds the maximum allowed duration of %d minutes (4 hours)",
                            durationSeconds / 60, maxDurationMinutes)
            );
        }
    }

    public void validateMagicBytes(InputStream inputStream, String filename) {
        try {
            if (!inputStream.markSupported()) {
                inputStream = new BufferedInputStream(inputStream);
            }
            inputStream.mark(64);

            byte[] header = new byte[64];
            int read = inputStream.read(header, 0, header.length);
            inputStream.reset();

            if (read < 8) {
                throw new BadRequestException(ErrorCode.UNSUPPORTED_FORMAT, "File is corrupted or too small to be a valid video");
            }

            // Explicit rejection of executable binaries disguised with a video extension
            if (isExecutableOrDangerousFile(header)) {
                log.warn("Executable signature detected in uploaded file '{}'. Upload rejected.", filename);
                throw new BadRequestException(
                        ErrorCode.UNSUPPORTED_FORMAT,
                        "Executable binary or script disguised as a video file is strictly prohibited"
                );
            }

            if (!hasValidVideoSignature(header)) {
                log.warn("File '{}' failed magic bytes signature verification", filename);
                throw new BadRequestException(
                        ErrorCode.UNSUPPORTED_FORMAT,
                        "File content does not match a recognized video signature (magic bytes check failed)"
                );
            }
        } catch (IOException e) {
            throw new BadRequestException(ErrorCode.PROCESSING_ERROR, "Failed to read file header signature: " + e.getMessage());
        }
    }

    private boolean isExecutableOrDangerousFile(byte[] h) {
        if (h == null || h.length < 4) {
            return false;
        }

        // Windows PE / DOS Executable: 'MZ' (0x4D, 0x5A)
        if (h[0] == 'M' && h[1] == 'Z') {
            return true;
        }

        // Linux ELF binary: 0x7F 'E' 'L' 'F'
        if ((h[0] & 0xFF) == 0x7F && h[1] == 'E' && h[2] == 'L' && h[3] == 'F') {
            return true;
        }

        // Script shebang: '#!' (0x23, 0x21)
        if (h[0] == '#' && h[1] == '!') {
            return true;
        }

        // Java bytecode: CA FE BA BE
        if ((h[0] & 0xFF) == 0xCA && (h[1] & 0xFF) == 0xFE && (h[2] & 0xFF) == 0xBA && (h[3] & 0xFF) == 0xBE) {
            return true;
        }

        return false;
    }

    private boolean hasValidVideoSignature(byte[] h) {
        // MP4 / MOV: 'ftyp' at offset 4
        if (h.length >= 8 && h[4] == 'f' && h[5] == 't' && h[6] == 'y' && h[7] == 'p') {
            return true;
        }

        // Matroska (MKV) / WebM: starts with 0x1A 0x45 0xDF 0xA3
        if (h.length >= 4 && (h[0] & 0xFF) == 0x1A && (h[1] & 0xFF) == 0x45 && (h[2] & 0xFF) == 0xDF && (h[3] & 0xFF) == 0xA3) {
            return true;
        }

        // AVI: RIFF....AVI
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' &&
                h[8] == 'A' && h[9] == 'V' && h[10] == 'I' && h[11] == ' ') {
            return true;
        }

        // FLV: 'FLV\x01'
        if (h.length >= 4 && h[0] == 'F' && h[1] == 'L' && h[2] == 'V' && h[3] == 0x01) {
            return true;
        }

        // WMV / ASF: 30 26 B2 75 8E 66 CF 11
        if (h.length >= 8 && (h[0] & 0xFF) == 0x30 && (h[1] & 0xFF) == 0x26 && (h[2] & 0xFF) == 0xB2 && (h[3] & 0xFF) == 0x75) {
            return true;
        }

        // MPEG: 00 00 01 BA or 00 00 01 B3
        if (h.length >= 4 && h[0] == 0x00 && h[1] == 0x00 && h[2] == 0x01 && ((h[3] & 0xFF) == 0xBA || (h[3] & 0xFF) == 0xB3)) {
            return true;
        }

        // QuickTime (moov / free / skip at offset 4)
        if (h.length >= 8 && (h[4] == 'm' && h[5] == 'o' && h[6] == 'o' && h[7] == 'v')) {
            return true;
        }

        return false;
    }
}
