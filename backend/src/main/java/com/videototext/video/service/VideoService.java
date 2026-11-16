package com.videototext.video.service;

import com.videototext.common.dto.PageResponse;
import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ErrorCode;
import com.videototext.common.exception.FileProcessingException;
import com.videototext.common.exception.ResourceNotFoundException;
import com.videototext.storage.service.CompletedPartDto;
import com.videototext.storage.service.StorageService;
import com.videototext.user.entity.User;
import com.videototext.user.repository.UserRepository;
import com.videototext.video.dto.CompleteUploadRequest;
import com.videototext.video.dto.InitiateUploadRequest;
import com.videototext.video.dto.InitiateUploadResponse;
import com.videototext.video.dto.VideoResponse;
import com.videototext.video.entity.UploadSession;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.UploadSessionRepository;
import com.videototext.video.repository.VideoRepository;
import com.videototext.video.validation.VideoValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoService {

    private final VideoRepository videoRepository;
    private final UploadSessionRepository uploadSessionRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final VideoValidator videoValidator;
    private final MetadataExtractionService metadataExtractionService;

    @Transactional
    public VideoResponse uploadVideo(MultipartFile file, UUID userId) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            throw new BadRequestException(ErrorCode.VALIDATION_ERROR, "Uploaded file must have a valid filename");
        }

        videoValidator.validateExtension(filename);
        videoValidator.validateFileSize(file.getSize());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        File tempFile = null;
        try {
            // Validate file signature / magic bytes
            try (InputStream is = file.getInputStream()) {
                videoValidator.validateMagicBytes(is, filename);
            }

            tempFile = File.createTempFile("vtt-upload-", "-" + filename);
            file.transferTo(tempFile);

            // Extract metadata and enforce 240-minute limit
            MetadataExtractionService.VideoMetadata metadata = metadataExtractionService.extractMetadata(tempFile);

            String storageKey = String.format("videos/%s/%s/%s", userId, UUID.randomUUID(), sanitizeFilename(filename));
            String contentType = file.getContentType() != null ? file.getContentType() : "video/mp4";

            // Upload to S3/MinIO
            try (FileInputStream fis = new FileInputStream(tempFile)) {
                storageService.uploadFile(storageKey, fis, tempFile.length(), contentType);
            }

            Video video = Video.builder()
                    .user(user)
                    .originalFilename(filename)
                    .storageKey(storageKey)
                    .contentType(contentType)
                    .fileSizeBytes(tempFile.length())
                    .durationSeconds(metadata.getDurationSeconds())
                    .codec(metadata.getCodec())
                    .resolution(metadata.getResolution())
                    .status("UPLOADED")
                    .build();

            Video saved = videoRepository.save(video);
            log.info("Video successfully uploaded and registered: id={}, user={}", saved.getId(), userId);

            return mapToResponse(saved);
        } catch (BadRequestException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to process video upload: {}", ex.getMessage(), ex);
            throw new FileProcessingException("Failed to process uploaded video: " + ex.getMessage(), ex);
        } finally {
            if (tempFile != null && tempFile.exists()) {
                boolean deleted = tempFile.delete();
                if (!deleted) {
                    tempFile.deleteOnExit();
                }
            }
        }
    }

    @Transactional
    public InitiateUploadResponse initiateMultipartUpload(InitiateUploadRequest request, UUID userId) {
        videoValidator.validateExtension(request.getFilename());
        videoValidator.validateFileSize(request.getFileSizeBytes());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String storageKey = String.format("videos/%s/%s/%s", userId, UUID.randomUUID(), sanitizeFilename(request.getFilename()));
        String contentType = request.getContentType() != null ? request.getContentType() : "video/mp4";

        String s3UploadId = storageService.initiateMultipartUpload(storageKey, contentType);

        Video video = Video.builder()
                .user(user)
                .originalFilename(request.getFilename())
                .storageKey(storageKey)
                .contentType(contentType)
                .fileSizeBytes(request.getFileSizeBytes())
                .durationSeconds(0) // Will be updated on completion
                .status("UPLOADING")
                .build();
        Video savedVideo = videoRepository.save(video);

        long partSize = (long) Math.ceil((double) request.getFileSizeBytes() / request.getTotalParts());

        UploadSession session = UploadSession.builder()
                .video(savedVideo)
                .uploadType("MULTIPART")
                .status("UPLOADING")
                .totalBytes(request.getFileSizeBytes())
                .totalParts(request.getTotalParts())
                .uploadId(s3UploadId)
                .startedAt(Instant.now())
                .build();
        UploadSession savedSession = uploadSessionRepository.save(session);

        return InitiateUploadResponse.builder()
                .uploadSessionId(savedSession.getId())
                .videoId(savedVideo.getId())
                .partSize(partSize)
                .totalParts(request.getTotalParts())
                .build();
    }

    @Transactional
    public String uploadPart(UUID sessionId, int partNumber, MultipartFile partFile, UUID userId) {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("UploadSession", "id", sessionId));

        Video video = session.getVideo();
        if (video == null || !video.getUser().getId().equals(userId)) {
            throw new BadRequestException(ErrorCode.ACCESS_DENIED, "Unauthorized to upload part to this session");
        }

        try (InputStream is = partFile.getInputStream()) {
            String eTag = storageService.uploadPart(video.getStorageKey(), session.getUploadId(), partNumber, is, partFile.getSize());
            session.setPartsCompleted(session.getPartsCompleted() + 1);
            session.setBytesUploaded(session.getBytesUploaded() + partFile.getSize());
            uploadSessionRepository.save(session);
            return eTag;
        } catch (Exception ex) {
            throw new FileProcessingException("Failed to upload part " + partNumber + ": " + ex.getMessage(), ex);
        }
    }

    @Transactional
    public VideoResponse completeMultipartUpload(UUID sessionId, CompleteUploadRequest request, UUID userId) {
        UploadSession session = uploadSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("UploadSession", "id", sessionId));

        Video video = session.getVideo();
        if (video == null || !video.getUser().getId().equals(userId)) {
            throw new BadRequestException(ErrorCode.ACCESS_DENIED, "Unauthorized to complete this upload session");
        }

        storageService.completeMultipartUpload(video.getStorageKey(), session.getUploadId(), request.getParts());

        session.setStatus("COMPLETED");
        session.setCompletedAt(Instant.now());
        uploadSessionRepository.save(session);

        video.setStatus("UPLOADED");
        Video updated = videoRepository.save(video);

        return mapToResponse(updated);
    }

    @Transactional(readOnly = true)
    public PageResponse<VideoResponse> listVideos(UUID userId, String status, Pageable pageable) {
        Page<Video> page;
        if (status != null && !status.isBlank()) {
            page = videoRepository.findByUserIdAndStatus(userId, status.toUpperCase(), pageable);
        } else {
            page = videoRepository.findByUserId(userId, pageable);
        }

        return PageResponse.of(page.map(this::mapToResponse));
    }

    @Transactional(readOnly = true)
    public VideoResponse getVideo(UUID videoId, UUID userId) {
        Video video = videoRepository.findByIdAndUserId(videoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Video", "id", videoId));

        return mapToResponse(video);
    }

    @Transactional
    public void deleteVideo(UUID videoId, UUID userId) {
        Video video = videoRepository.findByIdAndUserId(videoId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Video", "id", videoId));

        try {
            storageService.deleteFile(video.getStorageKey());
        } catch (Exception ex) {
            log.warn("Failed to delete storage file for video {}: {}", videoId, ex.getMessage());
        }

        videoRepository.delete(video);
        log.info("Deleted video {} for user {}", videoId, userId);
    }

    private VideoResponse mapToResponse(Video video) {
        String downloadUrl = storageService.generatePresignedUrl(video.getStorageKey(), Duration.ofHours(1));

        return VideoResponse.builder()
                .id(video.getId())
                .originalFilename(video.getOriginalFilename())
                .fileSizeBytes(video.getFileSizeBytes())
                .durationSeconds(video.getDurationSeconds())
                .codec(video.getCodec())
                .resolution(video.getResolution())
                .contentType(video.getContentType())
                .status(video.getStatus())
                .downloadUrl(downloadUrl)
                .createdAt(video.getCreatedAt())
                .build();
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
