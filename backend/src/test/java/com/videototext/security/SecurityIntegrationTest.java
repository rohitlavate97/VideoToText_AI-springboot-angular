package com.videototext.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ErrorCode;
import com.videototext.security.audit.SecurityAuditService;
import com.videototext.security.quota.UploadQuotaService;
import com.videototext.security.ratelimit.RateLimitingService;
import com.videototext.storage.service.StorageService;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.transcription.service.DeadLetterConsumer;
import com.videototext.transcription.service.JobRecoveryService;
import com.videototext.user.entity.User;
import com.videototext.user.repository.UserRepository;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VideoRepository videoRepository;

    @Autowired
    private TranscriptionJobRepository jobRepository;

    @Autowired
    private UploadQuotaService uploadQuotaService;

    @Autowired
    private RateLimitingService rateLimitingService;

    @Autowired
    private JobRecoveryService jobRecoveryService;

    @Autowired
    private DeadLetterConsumer deadLetterConsumer;

    @Autowired
    private SecurityAuditService securityAuditService;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private static String tokenUser1;
    private static String tokenUser2;
    private static UUID user1Id;
    private static UUID user2Id;
    private static boolean initialized = false;

    private static byte[] createValidMp4Bytes() {
        byte[] bytes = new byte[128];
        bytes[0] = 0x00;
        bytes[1] = 0x00;
        bytes[2] = 0x00;
        bytes[3] = 0x18;
        bytes[4] = 'f';
        bytes[5] = 't';
        bytes[6] = 'y';
        bytes[7] = 'p';
        bytes[8] = 'i';
        bytes[9] = 's';
        bytes[10] = 'o';
        bytes[11] = 'm';
        return bytes;
    }

    @BeforeEach
    void setUp() throws Exception {
        when(storageService.generatePresignedUrl(anyString(), any(Duration.class)))
                .thenReturn("https://storage.videototext.local/bucket/presigned-url");
        when(storageService.initiateMultipartUpload(anyString(), anyString()))
                .thenReturn("mock-upload-id-12345");
        doNothing().when(storageService).uploadFile(anyString(), any(InputStream.class), anyLong(), anyString());
        doNothing().when(storageService).deleteFile(anyString());

        if (!initialized) {
            // Register User 1
            RegisterRequest req1 = RegisterRequest.builder()
                    .email("secuser1@example.com")
                    .password("Password123!")
                    .firstName("Sec")
                    .lastName("User1")
                    .build();

            MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andReturn();
            tokenUser1 = objectMapper.readTree(res1.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            User user1 = userRepository.findByEmail("secuser1@example.com").orElseThrow();
            user1Id = user1.getId();

            // Register User 2
            RegisterRequest req2 = RegisterRequest.builder()
                    .email("secuser2@example.com")
                    .password("Password123!")
                    .firstName("Sec")
                    .lastName("User2")
                    .build();

            MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andReturn();
            tokenUser2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            User user2 = userRepository.findByEmail("secuser2@example.com").orElseThrow();
            user2Id = user2.getId();

            initialized = true;
        }
    }

    @Test
    @Order(1)
    @DisplayName("Security: Reject executable Windows binary masquerading with .mp4 extension")
    void shouldRejectWindowsExecutableBinary() throws Exception {
        byte[] mzPayload = new byte[128];
        mzPayload[0] = (byte) 0x4D;
        mzPayload[1] = (byte) 0x5A;

        MockMultipartFile exeFile = new MockMultipartFile(
                "file",
                "binary_header.mp4",
                "video/mp4",
                mzPayload
        );

        mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(exeFile)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code", is(ErrorCode.UNSUPPORTED_FORMAT.getCode())));
    }

    @Test
    @Order(2)
    @DisplayName("Security: Reject Linux ELF binary masquerading with .mp4 extension")
    void shouldRejectLinuxElfBinary() throws Exception {
        byte[] elfPayload = new byte[128];
        elfPayload[0] = 0x7F;
        elfPayload[1] = (byte) 'E';
        elfPayload[2] = (byte) 'L';
        elfPayload[3] = (byte) 'F';

        MockMultipartFile elfFile = new MockMultipartFile(
                "file",
                "elf_header.mp4",
                "video/mp4",
                elfPayload
        );

        mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(elfFile)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code", is(ErrorCode.UNSUPPORTED_FORMAT.getCode())));
    }

    @Test
    @Order(3)
    @DisplayName("Security: Reject path traversal in uploaded filename")
    void shouldRejectPathTraversalFilename() throws Exception {
        MockMultipartFile maliciousMp4 = new MockMultipartFile(
                "file",
                "../../../../evil_script.mp4",
                "video/mp4",
                createValidMp4Bytes()
        );

        mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(maliciousMp4)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is(ErrorCode.VALIDATION_ERROR.getCode())));
    }

    @Test
    @Order(4)
    @DisplayName("Security: Sanitize characters in uploaded filename")
    void shouldSanitizeUploadedFilename() throws Exception {
        MockMultipartFile validMp4 = new MockMultipartFile(
                "file",
                "my report (2026) #1.mp4",
                "video/mp4",
                createValidMp4Bytes()
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(validMp4)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isCreated())
                .andReturn();

        String originalFilename = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("originalFilename").asText();

        assertFalse(originalFilename.contains(" "));
        assertFalse(originalFilename.contains("("));
        assertFalse(originalFilename.contains("#"));
        assertEquals("my_report__2026___1.mp4", originalFilename);
    }

    @Test
    @Order(5)
    @DisplayName("Security: Enforce storage quota when exceeded")
    void shouldEnforceStorageQuota() {
        long limit = uploadQuotaService.getMaxStoragePerUserBytes();
        long excessSize = limit + 1024L;

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> uploadQuotaService.checkUploadQuota(user1Id, excessSize)
        );

        assertEquals(ErrorCode.QUOTA_EXCEEDED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Storage quota exceeded"));
    }

    @Test
    @Order(6)
    @DisplayName("Security: Enforce concurrent job quota")
    void shouldEnforceConcurrentJobQuota() {
        User user = userRepository.findById(user1Id).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("test-quota.mp4")
                .storageKey("videos/" + user1Id + "/quota.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(1000L)
                .durationSeconds(60)
                .status("READY")
                .build());

        // Create 3 active jobs to fill quota limit
        for (int i = 0; i < 3; i++) {
            jobRepository.save(TranscriptionJob.builder()
                    .user(user)
                    .video(video)
                    .status("PROCESSING")
                    .currentStage("TRANSCRIBING")
                    .progressPercent(10.0f)
                    .build());
        }

        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> uploadQuotaService.checkJobQuota(user1Id)
        );

        assertEquals(ErrorCode.QUOTA_EXCEEDED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Concurrent processing job quota exceeded"));
    }

    @Test
    @Order(7)
    @DisplayName("Security: Enforce API sliding window rate limit with 429 response")
    void shouldEnforceRateLimiting() throws Exception {
        rateLimitingService.clear();

        // Auth endpoint limit is 20 req/min
        String testIp = "198.51.100.42";
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .header("X-Forwarded-For", testIp)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"fake@example.com\",\"password\":\"wrong\"}"))
                    .andExpect(status().isUnauthorized());
        }

        // 21st request should be rate-limited with HTTP 429
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-Forwarded-For", testIp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"fake@example.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-RateLimit-Remaining", "0"))
                .andExpect(jsonPath("$.error.code", is(ErrorCode.RATE_LIMIT_EXCEEDED.getCode())));
    }

    @Test
    @Order(8)
    @DisplayName("Reliability: Automatically recover stuck job via sweeper")
    void shouldRecoverStuckJobViaSweeper() {
        User user = userRepository.findById(user1Id).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("stuck-recovery.mp4")
                .storageKey("videos/" + user1Id + "/stuck.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(2048L)
                .durationSeconds(120)
                .status("PROCESSING")
                .build());

        TranscriptionJob stuckJob = jobRepository.save(TranscriptionJob.builder()
                .user(user)
                .video(video)
                .status("PROCESSING")
                .currentStage("CHUNKING")
                .progressPercent(25.0f)
                .retryCount(0)
                .maxRetries(3)
                .build());

        jobRecoveryService.recoverOrFailJob(stuckJob, "Simulated timeout in integration test");

        TranscriptionJob recovered = jobRepository.findById(stuckJob.getId()).orElseThrow();
        assertEquals("RETRYING", recovered.getStatus());
        assertEquals("RETRYING", recovered.getCurrentStage());
        assertEquals(1, recovered.getRetryCount());
    }

    @Test
    @Order(9)
    @DisplayName("Reliability: Mark job as FAILED when max retries exceeded")
    void shouldFailJobWhenMaxRetriesExceeded() {
        User user = userRepository.findById(user1Id).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("max-retries.mp4")
                .storageKey("videos/" + user1Id + "/max-retries.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(2048L)
                .durationSeconds(120)
                .status("PROCESSING")
                .build());

        TranscriptionJob maxRetryJob = jobRepository.save(TranscriptionJob.builder()
                .user(user)
                .video(video)
                .status("RETRYING")
                .currentStage("RETRYING")
                .progressPercent(30.0f)
                .retryCount(3)
                .maxRetries(3)
                .build());

        jobRecoveryService.recoverOrFailJob(maxRetryJob, "Exceeded retry threshold");

        TranscriptionJob failed = jobRepository.findById(maxRetryJob.getId()).orElseThrow();
        assertEquals("FAILED", failed.getStatus());
        assertEquals("FAILED", failed.getCurrentStage());
        assertNotNull(failed.getCompletedAt());
    }

    @Test
    @Order(10)
    @DisplayName("Reliability: Process Dead Letter Queue message and transition job to FAILED")
    void shouldProcessDeadLetterMessage() {
        User user = userRepository.findById(user1Id).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("dlq-test.mp4")
                .storageKey("videos/" + user1Id + "/dlq.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(2048L)
                .durationSeconds(120)
                .status("PROCESSING")
                .build());

        TranscriptionJob job = jobRepository.save(TranscriptionJob.builder()
                .user(user)
                .video(video)
                .status("PROCESSING")
                .currentStage("TRANSCRIBING")
                .progressPercent(40.0f)
                .build());

        String dlqPayload = String.format("{\"jobId\":\"%s\",\"videoId\":\"%s\"}", job.getId(), video.getId());
        MessageProperties properties = new MessageProperties();
        properties.setReceivedRoutingKey("transcription.request");
        Message message = new Message(dlqPayload.getBytes(StandardCharsets.UTF_8), properties);

        deadLetterConsumer.processDeadLetter(message);

        TranscriptionJob failedJob = jobRepository.findById(job.getId()).orElseThrow();
        assertEquals("FAILED", failedJob.getStatus());
        assertEquals("FAILED", failedJob.getCurrentStage());
        assertFalse(deadLetterConsumer.getRecentDeadLetters().isEmpty());
    }

    @Test
    @Order(11)
    @DisplayName("Security: Enforce user ownership and prevent cross-user resource deletion")
    void shouldPreventCrossUserAccess() throws Exception {
        User user1 = userRepository.findById(user1Id).orElseThrow();
        Video video1 = videoRepository.save(Video.builder()
                .user(user1)
                .originalFilename("user1-private.mp4")
                .storageKey("videos/" + user1Id + "/private.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(4096L)
                .durationSeconds(60)
                .status("READY")
                .build());

        // User 2 attempts to delete User 1's video
        mockMvc.perform(delete("/api/v1/videos/" + video1.getId())
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isNotFound());

        // Verify video still exists
        assertTrue(videoRepository.findById(video1.getId()).isPresent());
    }
}
