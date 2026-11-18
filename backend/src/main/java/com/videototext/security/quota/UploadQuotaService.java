package com.videototext.security.quota;

import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ErrorCode;
import com.videototext.security.audit.SecurityAuditService;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.video.repository.VideoRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UploadQuotaService {

    private final VideoRepository videoRepository;
    private final TranscriptionJobRepository jobRepository;
    private final SecurityAuditService securityAuditService;

    @Getter
    @Value("${app.quota.max-storage-per-user-bytes:21474836480}")
    private long maxStoragePerUserBytes;

    @Getter
    @Value("${app.quota.max-concurrent-processing-jobs:3}")
    private int maxConcurrentProcessingJobs;

    private static final List<String> ACTIVE_JOB_STATUSES = List.of("QUEUED", "PROCESSING");

    /**
     * Verifies that the user has enough storage quota remaining to upload the requested file.
     *
     * @param userId             User UUID
     * @param incomingSizeBytes  Size of incoming file in bytes
     * @throws BadRequestException if quota is exceeded
     */
    @Transactional(readOnly = true)
    public void checkUploadQuota(UUID userId, long incomingSizeBytes) {
        long currentStorageBytes = videoRepository.sumFileSizeBytesByUserId(userId);
        if (currentStorageBytes + incomingSizeBytes > maxStoragePerUserBytes) {
            log.warn("Storage quota exceeded for user {}: current={} bytes, incoming={} bytes, limit={} bytes",
                    userId, currentStorageBytes, incomingSizeBytes, maxStoragePerUserBytes);
            securityAuditService.logSecurityEvent(
                    "QUOTA_EXCEEDED",
                    userId.toString(),
                    null,
                    null,
                    "DENIED",
                    String.format("Storage quota exceeded: current=%d, incoming=%d, limit=%d",
                            currentStorageBytes, incomingSizeBytes, maxStoragePerUserBytes)
            );
            throw new BadRequestException(
                    ErrorCode.QUOTA_EXCEEDED,
                    String.format("Storage quota exceeded. Used: %d bytes, Incoming: %d bytes, Limit: %d bytes",
                            currentStorageBytes, incomingSizeBytes, maxStoragePerUserBytes)
            );
        }
    }

    /**
     * Verifies that the user has not exceeded the maximum number of concurrent active processing jobs.
     *
     * @param userId User UUID
     * @throws BadRequestException if job quota is exceeded
     */
    @Transactional(readOnly = true)
    public void checkJobQuota(UUID userId) {
        long activeJobs = jobRepository.countActiveJobsByUserId(userId, ACTIVE_JOB_STATUSES);
        if (activeJobs >= maxConcurrentProcessingJobs) {
            log.warn("Concurrent processing job quota exceeded for user {}: active={}, limit={}",
                    userId, activeJobs, maxConcurrentProcessingJobs);
            securityAuditService.logSecurityEvent(
                    "QUOTA_EXCEEDED",
                    userId.toString(),
                    null,
                    null,
                    "DENIED",
                    String.format("Concurrent job quota exceeded: active=%d, limit=%d",
                            activeJobs, maxConcurrentProcessingJobs)
            );
            throw new BadRequestException(
                    ErrorCode.QUOTA_EXCEEDED,
                    String.format("Concurrent processing job quota exceeded. Active jobs: %d, Limit: %d",
                            activeJobs, maxConcurrentProcessingJobs)
            );
        }
    }

    @Transactional(readOnly = true)
    public long getUserStorageUsage(UUID userId) {
        return videoRepository.sumFileSizeBytesByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getRemainingStorage(UUID userId) {
        long used = getUserStorageUsage(userId);
        return Math.max(0, maxStoragePerUserBytes - used);
    }

    @Transactional(readOnly = true)
    public long getActiveJobCount(UUID userId) {
        return jobRepository.countActiveJobsByUserId(userId, ACTIVE_JOB_STATUSES);
    }
}
