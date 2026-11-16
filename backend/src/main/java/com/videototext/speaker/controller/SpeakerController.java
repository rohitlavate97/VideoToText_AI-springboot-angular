package com.videototext.speaker.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.security.CustomUserDetails;
import com.videototext.speaker.dto.SpeakerResponse;
import com.videototext.speaker.dto.UpdateSpeakerRequest;
import com.videototext.speaker.service.SpeakerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transcriptions/{jobId}/speakers")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Speaker Diarization Management", description = "Query identified speakers, speaking statistics, and manage speaker labels/identities")
public class SpeakerController {

    private final SpeakerService speakerService;

    @Operation(summary = "List speakers for a transcription job", description = "Returns all identified speakers with cumulative speaking durations and turn counts")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<SpeakerResponse>>> getSpeakers(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<SpeakerResponse> response = speakerService.getSpeakers(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Rename / update speaker identity", description = "Renames a speaker label (e.g., 'SPEAKER_00' -> 'Dr. John Doe')")
    @PutMapping("/{speakerId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SpeakerResponse>> updateSpeaker(
            @PathVariable UUID jobId,
            @PathVariable UUID speakerId,
            @Valid @RequestBody UpdateSpeakerRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        SpeakerResponse response = speakerService.updateSpeaker(userDetails.getId(), jobId, speakerId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Speaker updated successfully"));
    }
}
