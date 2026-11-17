package com.videototext.subtitle.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.export.dto.ExportResponse;
import com.videototext.security.CustomUserDetails;
import com.videototext.subtitle.dto.GenerateSubtitleRequest;
import com.videototext.subtitle.dto.SubtitleResponse;
import com.videototext.subtitle.service.SubtitleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transcriptions/{jobId}")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Subtitles & Exports", description = "Generate and export subtitles in SRT, VTT, TXT, and JSON formats")
public class SubtitleController {

    private final SubtitleService subtitleService;

    @Operation(summary = "List subtitles", description = "List all generated subtitle files for a transcription job")
    @GetMapping("/subtitles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SubtitleResponse>>> getSubtitles(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<SubtitleResponse> responses = subtitleService.getSubtitlesForJob(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @Operation(summary = "Generate subtitle", description = "Generate a subtitle file in the specified format (SRT, VTT, TXT, JSON)")
    @PostMapping("/subtitles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SubtitleResponse>> generateSubtitle(
            @PathVariable UUID jobId,
            @Valid @RequestBody GenerateSubtitleRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SubtitleResponse response = subtitleService.generateSubtitle(userDetails.getId(), jobId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Subtitle generated successfully"));
    }

    @Operation(summary = "Download subtitle by query parameters", description = "Download a subtitle file with optional language and speaker label parameters")
    @GetMapping("/subtitles/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadSubtitle(
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "SRT") String format,
            @RequestParam(defaultValue = "en") String language,
            @RequestParam(defaultValue = "false") boolean includeSpeakers,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        byte[] content = subtitleService.downloadSubtitle(userDetails.getId(), jobId, format, language, includeSpeakers);
        String mimeType = subtitleService.getMimeType(format);
        String ext = subtitleService.getFileExtension(format);
        String filename = String.format("transcription_%s_%s%s", jobId, language, ext);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(content.length);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @Operation(summary = "Download subtitle by ID", description = "Download a specific subtitle artifact by its unique ID")
    @GetMapping("/subtitles/{subtitleId}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadSubtitleById(
            @PathVariable UUID jobId,
            @PathVariable UUID subtitleId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        byte[] content = subtitleService.downloadSubtitleById(userDetails.getId(), jobId, subtitleId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("subtitle_" + subtitleId + ".txt")
                .build());
        headers.setContentLength(content.length);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @Operation(summary = "Delete subtitle", description = "Deletes a generated subtitle file")
    @DeleteMapping("/subtitles/{subtitleId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteSubtitle(
            @PathVariable UUID jobId,
            @PathVariable UUID subtitleId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        subtitleService.deleteSubtitle(userDetails.getId(), jobId, subtitleId);
        return ResponseEntity.ok(ApiResponse.success(null, "Subtitle deleted successfully"));
    }

    @Operation(summary = "Export transcript", description = "Export transcript in various formats with metadata auditing")
    @GetMapping("/export")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportTranscript(
            @PathVariable UUID jobId,
            @RequestParam(defaultValue = "TXT") String format,
            @RequestParam(defaultValue = "en") String language,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        byte[] content = subtitleService.exportTranscript(userDetails.getId(), jobId, format, language);
        String mimeType = subtitleService.getMimeType(format);
        String ext = subtitleService.getFileExtension(format);
        String filename = String.format("export_%s_%s%s", jobId, language, ext);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        headers.setContentLength(content.length);

        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @Operation(summary = "List exports", description = "Retrieves export history for a transcription job")
    @GetMapping("/exports")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ExportResponse>>> getExports(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<ExportResponse> responses = subtitleService.getExportsForJob(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(responses));
    }
}
