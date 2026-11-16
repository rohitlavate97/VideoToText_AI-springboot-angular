package com.videototext.video.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.common.dto.PageResponse;
import com.videototext.security.CustomUserDetails;
import com.videototext.video.dto.CompleteUploadRequest;
import com.videototext.video.dto.InitiateUploadRequest;
import com.videototext.video.dto.InitiateUploadResponse;
import com.videototext.video.dto.VideoResponse;
import com.videototext.video.service.VideoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Video Management", description = "Video ingestion, multipart upload, validation, library listing, and deletion")
public class VideoController {

    private final VideoService videoService;

    @Operation(summary = "Upload video file (Multipart)", description = "Direct multipart upload with signature and duration validation (max 240 minutes)")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VideoResponse>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        VideoResponse response = videoService.uploadVideo(file, userDetails.getId());
        return new ResponseEntity<>(ApiResponse.success(response, "Video uploaded and verified successfully"), HttpStatus.CREATED);
    }

    @Operation(summary = "Initiate multipart upload session", description = "Prepares an S3 multipart upload session for large video files")
    @PostMapping("/upload/initiate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<InitiateUploadResponse>> initiateUpload(
            @Valid @RequestBody InitiateUploadRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        InitiateUploadResponse response = videoService.initiateMultipartUpload(request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Upload session initiated successfully"));
    }

    @Operation(summary = "Upload single part", description = "Uploads a chunk part to an existing multipart upload session")
    @PutMapping(value = "/upload/{sessionId}/parts/{partNumber}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadPart(
            @PathVariable UUID sessionId,
            @PathVariable int partNumber,
            @RequestParam("file") MultipartFile partFile,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        String eTag = videoService.uploadPart(sessionId, partNumber, partFile, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(Map.of("partNumber", partNumber, "eTag", eTag), "Part uploaded successfully"));
    }

    @Operation(summary = "Complete multipart upload session", description = "Finalizes the multipart upload and triggers media validation")
    @PostMapping("/upload/{sessionId}/complete")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VideoResponse>> completeUpload(
            @PathVariable UUID sessionId,
            @RequestBody CompleteUploadRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        VideoResponse response = videoService.completeMultipartUpload(sessionId, request, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Multipart upload completed successfully"));
    }

    @Operation(summary = "List videos", description = "Retrieves paginated list of uploaded videos for the authenticated user")
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PageResponse<VideoResponse>>> listVideos(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        PageResponse<VideoResponse> response = videoService.listVideos(userDetails.getId(), status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Videos retrieved successfully"));
    }

    @Operation(summary = "Get video details", description = "Retrieves video metadata and presigned streaming/download URL")
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<VideoResponse>> getVideo(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        VideoResponse response = videoService.getVideo(id, userDetails.getId());
        return ResponseEntity.ok(ApiResponse.success(response, "Video details retrieved successfully"));
    }

    @Operation(summary = "Delete video", description = "Deletes video metadata and removes media file from object storage")
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteVideo(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        videoService.deleteVideo(id, userDetails.getId());
        return ResponseEntity.noContent().build();
    }
}
