package com.videototext.transcription.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.security.CustomUserDetails;
import com.videototext.transcription.dto.CreateTranscriptionRequest;
import com.videototext.transcription.dto.TranscriptSegmentResponse;
import com.videototext.transcription.dto.TranscriptionJobResponse;
import com.videototext.transcription.service.TranscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transcriptions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Transcription Orchestration", description = "Manage transcription jobs, track lifecycle progress, and fetch transcript segments")
public class TranscriptionController {

    private final TranscriptionService transcriptionService;

    @Operation(summary = "Submit transcription job", description = "Creates a new transcription job and queues it for processing")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TranscriptionJobResponse>> createJob(
            @Valid @RequestBody CreateTranscriptionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TranscriptionJobResponse response = transcriptionService.createJob(userDetails.getId(), request);
        return new ResponseEntity<>(ApiResponse.success(response, "Transcription job queued successfully"), HttpStatus.CREATED);
    }

    @Operation(summary = "Get transcription job status", description = "Fetches job metadata, current stage, and progress percentage")
    @GetMapping("/{jobId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TranscriptionJobResponse>> getJob(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TranscriptionJobResponse response = transcriptionService.getJob(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "List current user transcription jobs", description = "Returns chronological list of all transcription jobs for the authenticated user")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TranscriptionJobResponse>>> getUserJobs(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<TranscriptionJobResponse> response = transcriptionService.getUserJobs(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "List transcription jobs for a video", description = "Returns all transcription jobs initiated for a specific video")
    @GetMapping("/video/{videoId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TranscriptionJobResponse>>> getVideoJobs(
            @PathVariable UUID videoId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<TranscriptionJobResponse> response = transcriptionService.getVideoJobs(userDetails.getId(), videoId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get transcript segments", description = "Returns word-level aligned transcript segments with speaker IDs and timestamps")
    @GetMapping("/{jobId}/segments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TranscriptSegmentResponse>>> getJobSegments(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<TranscriptSegmentResponse> response = transcriptionService.getJobSegments(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Cancel transcription job", description = "Cancels an ongoing or queued transcription job")
    @PostMapping("/{jobId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TranscriptionJobResponse>> cancelJob(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TranscriptionJobResponse response = transcriptionService.cancelJob(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(response, "Transcription job cancelled successfully"));
    }
}
