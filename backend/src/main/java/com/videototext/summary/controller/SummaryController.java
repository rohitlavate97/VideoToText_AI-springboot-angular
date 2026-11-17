package com.videototext.summary.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.security.CustomUserDetails;
import com.videototext.summary.dto.GenerateSummaryRequest;
import com.videototext.summary.dto.SummaryResponse;
import com.videototext.summary.service.SummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transcriptions/{jobId}/summary")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Summaries & Insights", description = "Generate and retrieve video summaries, key takeaways, action items, topics, and chapters")
public class SummaryController {

    private final SummaryService summaryService;

    @Operation(summary = "Get summary for job", description = "Retrieves the generated summary and intelligent insights for a transcription job")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SummaryResponse>> getSummary(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SummaryResponse response = summaryService.getSummary(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Generate or regenerate summary", description = "Generates video summary, key points, action items, topics, and chapters according to requested options")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SummaryResponse>> generateSummary(
            @PathVariable UUID jobId,
            @RequestBody(required = false) GenerateSummaryRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        GenerateSummaryRequest req = request != null ? request : new GenerateSummaryRequest();
        SummaryResponse response = summaryService.generateSummary(userDetails.getId(), jobId, req);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Summary generated successfully"));
    }

    @Operation(summary = "Delete summary", description = "Removes the summary and insights for a transcription job")
    @DeleteMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deleteSummary(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        summaryService.deleteSummary(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(null, "Summary deleted successfully"));
    }
}
