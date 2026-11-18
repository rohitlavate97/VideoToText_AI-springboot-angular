package com.videototext.transcription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.storage.service.StorageService;
import com.videototext.transcription.dto.JobProgressEventDto;
import com.videototext.transcription.entity.ProcessingEventRecord;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.ProcessingEventRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.transcription.service.SseEmitterService;
import com.videototext.transcription.service.TranscriptionEventConsumer;
import com.videototext.transcription.service.TranscriptionService;
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
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventDeliveryIntegrationTest {

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
    private ProcessingEventRepository eventRepository;

    @Autowired
    private SseEmitterService sseEmitterService;

    @Autowired
    private TranscriptionEventConsumer eventConsumer;

    @Autowired
    private TranscriptionService transcriptionService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private StorageService storageService;

    private static User testUser;
    private static Video testVideo;
    private static TranscriptionJob testJob;
    private static String userToken;
    private static boolean seeded = false;

    @BeforeEach
    void setUp() throws Exception {
        if (!seeded) {
            RegisterRequest req = RegisterRequest.builder()
                    .email("eventtester@example.com")
                    .password("Password123!")
                    .firstName("Event")
                    .lastName("Tester")
                    .build();

            MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn();

            userToken = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            testUser = userRepository.findByEmail("eventtester@example.com").orElseThrow();

            testVideo = Video.builder()
                    .user(testUser)
                    .originalFilename("events_demo.mp4")
                    .storageKey("videos/events_demo.mp4")
                    .contentType("video/mp4")
                    .fileSizeBytes(1024L * 1024L)
                    .durationSeconds(120)
                    .status("PROCESSING")
                    .build();
            testVideo = videoRepository.save(testVideo);

            testJob = TranscriptionJob.builder()
                    .video(testVideo)
                    .user(testUser)
                    .status("PROCESSING")
                    .asrEngine("faster-whisper")
                    .asrModel("base")
                    .sourceLanguage("en")
                    .currentStage("INITIALIZED")
                    .progressPercent(0.0f)
                    .totalChunks(4)
                    .completedChunks(0)
                    .build();
            testJob = jobRepository.save(testJob);

            seeded = true;
        }
    }

    @Test
    @Order(1)
    @DisplayName("Should successfully establish SSE stream connection for an existing job")
    void testStreamJobEvents() throws Exception {
        MvcResult mvcResult = mockMvc.perform(get("/api/v1/events/jobs/" + testJob.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        assertNotNull(mvcResult);
        assertNotNull(mvcResult.getResponse());
        assertTrue(mvcResult.getResponse().getContentType().contains(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    @Order(2)
    @DisplayName("Should consume pipeline progress event and update database record and audit history")
    void testHandleProcessingEvent() {
        JobProgressEventDto progressEvent = JobProgressEventDto.builder()
                .jobId(testJob.getId())
                .stage("TRANSCRIBING")
                .progressPercent(50.0f)
                .completedChunks(2)
                .totalChunks(4)
                .message("Transcribing chunk 2 of 4")
                .timestamp(Instant.now())
                .build();

        eventConsumer.handleProcessingEvent(progressEvent);

        TranscriptionJob updatedJob = jobRepository.findById(testJob.getId()).orElseThrow();
        assertEquals("TRANSCRIBING", updatedJob.getCurrentStage());
        assertEquals(50.0f, updatedJob.getProgressPercent(), 0.01f);
        assertEquals(2, updatedJob.getCompletedChunks());
        assertEquals(4, updatedJob.getTotalChunks());

        List<ProcessingEventRecord> records = eventRepository.findByJobIdOrderByCreatedAtAsc(testJob.getId());
        assertFalse(records.isEmpty());
        ProcessingEventRecord lastRecord = records.get(records.size() - 1);
        assertEquals("TRANSCRIBING", lastRecord.getStage());
        assertEquals("Transcribing chunk 2 of 4", lastRecord.getMessage());
        assertEquals(50.0f, lastRecord.getProgressPercent(), 0.01f);
    }

    @Test
    @Order(3)
    @DisplayName("Should capture retry event and persist retryAttempt, maxRetries and errorMessage")
    void testHandleRetryProcessingEvent() {
        JobProgressEventDto retryEvent = JobProgressEventDto.builder()
                .jobId(testJob.getId())
                .stage("RETRYING")
                .progressPercent(50.0f)
                .completedChunks(2)
                .totalChunks(4)
                .retryAttempt(1)
                .maxRetries(3)
                .errorMessage("GPU worker timeout while transcribing chunk 3")
                .message("Processing attempt 1/3 failed. Retrying in 2.0s...")
                .timestamp(Instant.now())
                .build();

        eventConsumer.handleProcessingEvent(retryEvent);

        TranscriptionJob updatedJob = jobRepository.findById(testJob.getId()).orElseThrow();
        assertEquals("RETRYING", updatedJob.getStatus());
        assertEquals("RETRYING", updatedJob.getCurrentStage());
        assertEquals(1, updatedJob.getRetryCount());
        assertEquals(3, updatedJob.getMaxRetries());
        assertEquals("GPU worker timeout while transcribing chunk 3", updatedJob.getErrorMessage());

        List<ProcessingEventRecord> records = eventRepository.findByJobIdOrderByCreatedAtAsc(testJob.getId());
        ProcessingEventRecord retryRecord = records.get(records.size() - 1);
        assertEquals("RETRYING", retryRecord.getStage());
        assertNotNull(retryRecord.getMetadataJson());
        assertTrue(retryRecord.getMetadataJson().contains("retryAttempt"));
    }

    @Test
    @Order(4)
    @DisplayName("Should retrieve complete chronological event history via REST endpoint")
    void testGetJobEventHistory() throws Exception {
        mockMvc.perform(get("/api/v1/events/jobs/" + testJob.getId() + "/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$.data[0].jobId").value(testJob.getId().toString()))
                .andExpect(jsonPath("$.data[0].stage").isNotEmpty());
    }

    @Test
    @Order(5)
    @DisplayName("Should manage SSE subscriptions, broadcasts, and subscriber cleanup correctly")
    void testSseEmitterServiceLifecycle() {
        UUID tempJobId = UUID.randomUUID();

        TranscriptionJob dummyJob = TranscriptionJob.builder()
                .video(testVideo)
                .user(testUser)
                .status("PROCESSING")
                .currentStage("VAD_CHUNKING")
                .progressPercent(20.0f)
                .totalChunks(2)
                .completedChunks(0)
                .build();
        final TranscriptionJob savedDummyJob = jobRepository.save(dummyJob);
        final UUID dummyJobId = savedDummyJob.getId();

        // 1. Subscribe
        SseEmitter emitter = sseEmitterService.subscribe(dummyJobId);
        assertNotNull(emitter);
        assertEquals(1, sseEmitterService.getSubscriberCount(dummyJobId));

        // 2. Broadcast Progress
        JobProgressEventDto evt = JobProgressEventDto.builder()
                .jobId(dummyJobId)
                .stage("TRANSCRIBING")
                .progressPercent(60.0f)
                .completedChunks(1)
                .totalChunks(2)
                .message("Halfway done")
                .build();
        assertDoesNotThrow(() -> sseEmitterService.broadcastProgress(dummyJobId, evt));

        // 3. Broadcast Completion and verify subscriber cleanup
        sseEmitterService.broadcastCompletion(dummyJobId, "COMPLETED", "Finished transcription");
        assertEquals(0, sseEmitterService.getSubscriberCount(dummyJobId));
    }

    @Test
    @Order(6)
    @DisplayName("Should broadcast CANCELLED event and close SSE emitters when job is cancelled")
    void testCancelJobBroadcastsEvent() {
        TranscriptionJob cancelableJob = TranscriptionJob.builder()
                .video(testVideo)
                .user(testUser)
                .status("PROCESSING")
                .currentStage("TRANSCRIBING")
                .progressPercent(40.0f)
                .build();
        cancelableJob = jobRepository.save(cancelableJob);

        sseEmitterService.subscribe(cancelableJob.getId());
        assertEquals(1, sseEmitterService.getSubscriberCount(cancelableJob.getId()));

        transcriptionService.cancelJob(testUser.getId(), cancelableJob.getId());

        TranscriptionJob cancelled = jobRepository.findById(cancelableJob.getId()).orElseThrow();
        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("CANCELLED", cancelled.getCurrentStage());
        assertEquals(0, sseEmitterService.getSubscriberCount(cancelableJob.getId()));
    }
}
