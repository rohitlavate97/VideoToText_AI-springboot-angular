package com.videototext.transcription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.storage.service.StorageService;
import com.videototext.transcription.dto.CreateTranscriptionRequest;
import com.videototext.transcription.dto.JobProgressEventDto;
import com.videototext.transcription.dto.TranscriptionResultPayload;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.transcription.repository.TranscriptRevisionRepository;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.service.TranscriptionEventConsumer;
import com.videototext.user.entity.User;
import com.videototext.user.repository.UserRepository;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.VideoRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TranscriptionControllerIntegrationTest {

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
    private TranscriptSegmentRepository segmentRepository;

    @Autowired
    private TranscriptRevisionRepository revisionRepository;

    @Autowired
    private TranscriptionEventConsumer eventConsumer;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private StorageService storageService;

    private static String tokenUser1;
    private static String tokenUser2;
    private static UUID user1Id;
    private static UUID video1Id;
    private static UUID createdJobId;
    private static boolean initialized = false;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            // Register User 1
            RegisterRequest req1 = RegisterRequest.builder()
                    .email("jobtester1@example.com")
                    .password("Password123!")
                    .firstName("Job")
                    .lastName("Tester")
                    .build();

            MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser1 = objectMapper.readTree(res1.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            User user1 = userRepository.findByEmail("jobtester1@example.com").orElseThrow();
            user1Id = user1.getId();

            // Register User 2
            RegisterRequest req2 = RegisterRequest.builder()
                    .email("jobtester2@example.com")
                    .password("Password123!")
                    .firstName("Second")
                    .lastName("Tester")
                    .build();

            MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            // Seed a Video owned by User 1
            Video v = Video.builder()
                    .user(user1)
                    .originalFilename("transcription_test.mp4")
                    .storageKey("videos/" + user1Id + "/transcription_test.mp4")
                    .contentType("video/mp4")
                    .fileSizeBytes(5000000L)
                    .durationSeconds(120)
                    .status("UPLOADED")
                    .build();
            v = videoRepository.save(v);
            video1Id = v.getId();

            initialized = true;
        }
    }

    @Test
    @Order(1)
    void submitJob_Success() throws Exception {
        CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
                .videoId(video1Id)
                .engine("faster-whisper")
                .model("small")
                .language("en")
                .enableDiarization(true)
                .targetLanguages(List.of("es", "fr"))
                .generateSummary(true)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/transcriptions")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNotEmpty())
                .andExpect(jsonPath("$.data.videoId").value(video1Id.toString()))
                .andExpect(jsonPath("$.data.status").value("QUEUED"))
                .andExpect(jsonPath("$.data.engine").value("faster-whisper"))
                .andExpect(jsonPath("$.data.model").value("small"))
                .andReturn();

        String idStr = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("id").asText();
        createdJobId = UUID.fromString(idStr);
        assertNotNull(createdJobId);
    }

    @Test
    @Order(2)
    void getJob_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + createdJobId)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(createdJobId.toString()))
                .andExpect(jsonPath("$.data.videoId").value(video1Id.toString()));
    }

    @Test
    @Order(3)
    void getUserJobs_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(4)
    void getVideoJobs_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/video/" + video1Id)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(createdJobId.toString()));
    }

    @Test
    @Order(5)
    void getJob_UnauthorizedAccess_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + createdJobId)
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(6)
    void submitJob_NonExistentVideo_NotFound() throws Exception {
        CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
                .videoId(UUID.randomUUID())
                .build();

        mockMvc.perform(post("/api/v1/transcriptions")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void getJobSegments_EmptyInitialList_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + createdJobId + "/segments")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @Order(8)
    void eventConsumer_ProcessingEventUpdate_Success() {
        JobProgressEventDto progressEvent = JobProgressEventDto.builder()
                .jobId(createdJobId)
                .stage("TRANSCRIBING")
                .progressPercent(45.5f)
                .completedChunks(2)
                .totalChunks(5)
                .message("Transcribing chunk 2 of 5...")
                .timestamp(Instant.now())
                .build();

        eventConsumer.handleProcessingEvent(progressEvent);

        TranscriptionJob updated = jobRepository.findById(createdJobId).orElseThrow();
        assertEquals("PROCESSING", updated.getStatus());
        assertEquals("TRANSCRIBING", updated.getCurrentStage());
        assertEquals(45.5f, updated.getProgressPercent(), 0.01);
        assertEquals(2, updated.getCompletedChunks());
        assertEquals(5, updated.getTotalChunks());
    }

    @Test
    @Order(9)
    void eventConsumer_ResultEventCompletion_Success() {
        TranscriptionResultPayload resultPayload = TranscriptionResultPayload.builder()
                .jobId(createdJobId)
                .videoId(video1Id)
                .status("COMPLETED")
                .durationSeconds(120.0)
                .detectedLanguage("en")
                .segmentsCount(18)
                .speakersCount(2)
                .completedAt(Instant.now())
                .build();

        eventConsumer.handleTranscriptionResult(resultPayload);

        TranscriptionJob completed = jobRepository.findById(createdJobId).orElseThrow();
        assertEquals("COMPLETED", completed.getStatus());
        assertEquals("COMPLETED", completed.getCurrentStage());
        assertEquals(100.0f, completed.getProgressPercent(), 0.01);
        assertNotNull(completed.getCompletedAt());

        Video video = videoRepository.findById(video1Id).orElseThrow();
        assertEquals("READY", video.getStatus());
    }

    @Test
    @Order(10)
    void cancelJob_Success() throws Exception {
        // Create another job to test cancel
        CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
                .videoId(video1Id)
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/transcriptions")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String jobId = objectMapper.readTree(res.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/v1/transcriptions/" + jobId + "/cancel")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.currentStage").value("CANCELLED"));
    }

    @Test
    @Order(11)
    void getTranscript_Success() throws Exception {
        TranscriptionJob job = jobRepository.findById(createdJobId).orElseThrow();

        // Seed 2 sample segments for createdJobId
        TranscriptSegment seg1 = TranscriptSegment.builder()
                .job(job)
                .segmentIndex(0)
                .startTime(0.0f)
                .endTime(2.5f)
                .text("Welcome to the video transcription presentation.")
                .confidence(0.98f)
                .language("en")
                .build();
        TranscriptSegment seg2 = TranscriptSegment.builder()
                .job(job)
                .segmentIndex(1)
                .startTime(2.5f)
                .endTime(5.0f)
                .text("Today we will discuss speech recognition architectures.")
                .confidence(0.96f)
                .language("en")
                .build();
        segmentRepository.saveAll(List.of(seg1, seg2));

        mockMvc.perform(get("/api/v1/transcriptions/" + createdJobId + "/transcript")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(createdJobId.toString()))
                .andExpect(jsonPath("$.data.totalSegments").value(2))
                .andExpect(jsonPath("$.data.totalWords").value(13))
                .andExpect(jsonPath("$.data.fullText", containsString("Welcome to the video transcription")))
                .andExpect(jsonPath("$.data.segments", hasSize(2)));
    }

    @Test
    @Order(12)
    void getJobSegmentsPaginated_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + createdJobId + "/segments?page=0&size=1")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(1)));
    }

    @Test
    @Order(13)
    void getRevisions_EmptyInitialList_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + createdJobId + "/revisions")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
