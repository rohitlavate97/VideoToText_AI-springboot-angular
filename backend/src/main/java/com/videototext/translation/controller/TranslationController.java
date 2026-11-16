package com.videototext.translation.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.security.CustomUserDetails;
import com.videototext.translation.dto.CreateTranslationRequest;
import com.videototext.translation.dto.TranslationResponse;
import com.videototext.translation.service.TranslationService;
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
@RequestMapping("/api/v1/transcriptions/{jobId}/translations")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Multilingual Translation", description = "Manage translated transcript versions with preserved timestamps and speaker attributions")
public class TranslationController {

    private final TranslationService translationService;

    @Operation(summary = "List translations for job", description = "Returns all available language translations for a transcription job")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<TranslationResponse>>> getTranslations(
            @PathVariable UUID jobId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        List<TranslationResponse> response = translationService.getTranslationsForJob(userDetails.getId(), jobId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Get translation by language", description = "Returns the translated transcript with preserved timestamps and speaker IDs for a specific language code (e.g. 'hi', 'mr')")
    @GetMapping("/{language}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TranslationResponse>> getTranslation(
            @PathVariable UUID jobId,
            @PathVariable String language,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TranslationResponse response = translationService.getTranslation(userDetails.getId(), jobId, language);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "Request translation", description = "Initiates or synthesizes a translated transcript in a target language")
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<TranslationResponse>> requestTranslation(
            @PathVariable UUID jobId,
            @Valid @RequestBody CreateTranslationRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        TranslationResponse response = translationService.requestTranslation(userDetails.getId(), jobId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Translation created successfully"));
    }
}
