package com.videototext.summary.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.storage.service.StorageService;
import com.videototext.summary.dto.GenerateSummaryRequest;
import com.videototext.summary.repository.SummaryRepository;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SummaryControllerIntegrationTest {

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
    private SpeakerRepository speakerRepository;

    @Autowired
    private SummaryRepository summaryRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private StorageService storageService;

    private static String tokenUser1;
    private static String tokenUser2;
    private static UUID job1Id;
    private static boolean initialized = false;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            // Register User 1
            RegisterRequest req1 = RegisterRequest.builder()
                    .email("summaryuser1@example.com")
                    .password("Password123!")
                    .firstName("Summary")
                    .lastName("Owner")
                    .build();

            MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser1 = objectMapper.readTree(res1.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            User user1 = userRepository.findByEmail("summaryuser1@example.com").orElseThrow();

            // Register User 2
            RegisterRequest req2 = RegisterRequest.builder()
                    .email("summaryuser2@example.com")
                    .password("Password123!")
                    .firstName("Other")
                    .lastName("Observer")
                    .build();

            MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            // Seed Video
            Video video = Video.builder()
                    .user(user1)
                    .originalFilename("ai_architecture_sync.mp4")
                    .storageKey("videos/" + user1.getId() + "/ai_architecture_sync.mp4")
                    .contentType("video/mp4")
                    .fileSizeBytes(2048000L)
                    .durationSeconds(120)
                    .status("UPLOADED")
                    .build();
            video = videoRepository.save(video);

            // Seed Completed Job
            TranscriptionJob job = TranscriptionJob.builder()
                    .user(user1)
                    .video(video)
                    .status("COMPLETED")
                    .asrEngine("faster-whisper")
                    .asrModel("small")
                    .sourceLanguage("en")
                    .build();
            job = jobRepository.save(job);
            job1Id = job.getId();

            // Seed Speakers
            Speaker spk1 = Speaker.builder()
                    .job(job)
                    .speakerLabel("SPEAKER_00")
                    .displayName("Chief Architect")
                    .build();
            spk1 = speakerRepository.save(spk1);

            Speaker spk2 = Speaker.builder()
                    .job(job)
                    .speakerLabel("SPEAKER_01")
                    .displayName("Lead Engineer")
                    .build();
            spk2 = speakerRepository.save(spk2);

            // Seed Segments
            TranscriptSegment seg1 = TranscriptSegment.builder()
                    .job(job)
                    .speakerId(spk1.getId())
                    .segmentIndex(0)
                    .startTime(0.0f)
                    .endTime(5.0f)
                    .text("Welcome everyone to the quarterly system review. Today we evaluate architecture scaling.")
                    .confidence(0.98f)
                    .build();
            segmentRepository.save(seg1);

            TranscriptSegment seg2 = TranscriptSegment.builder()
                    .job(job)
                    .speakerId(spk2.getId())
                    .segmentIndex(1)
                    .startTime(5.0f)
                    .endTime(12.0f)
                    .text("We need to optimize the database query latency and add indexes before the production launch.")
                    .confidence(0.96f)
                    .build();
            segmentRepository.save(seg2);

            TranscriptSegment seg3 = TranscriptSegment.builder()
                    .job(job)
                    .speakerId(spk2.getId())
                    .segmentIndex(2)
                    .startTime(12.0f)
                    .endTime(18.0f)
                    .text("I will run automated benchmarks on the connection pool and report back tomorrow.")
                    .confidence(0.95f)
                    .build();
            segmentRepository.save(seg3);

            TranscriptSegment seg4 = TranscriptSegment.builder()
                    .job(job)
                    .speakerId(spk1.getId())
                    .segmentIndex(3)
                    .startTime(18.0f)
                    .endTime(25.0f)
                    .text("Make sure to coordinate with security for the compliance review next week.")
                    .confidence(0.97f)
                    .build();
            segmentRepository.save(seg4);

            initialized = true;
        }
    }

    @Test
    @Order(1)
    void getSummary_EmptyInitial_Throws404() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/summary")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(2)
    void generateSummary_Default_Success() throws Exception {
        GenerateSummaryRequest req = GenerateSummaryRequest.builder()
                .summaryLength("medium")
                .summaryFormat("paragraph")
                .extractKeyPoints(true)
                .extractActionItems(true)
                .extractTopics(true)
                .generateChapters(true)
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/" + job1Id + "/summary")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(job1Id.toString()))
                .andExpect(jsonPath("$.data.summaryText", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.actionItems", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.topics", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.chapters", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.llmProvider").value("local"));
    }

    @Test
    @Order(3)
    void getSummary_AfterGenerate_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/summary")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobId").value(job1Id.toString()))
                .andExpect(jsonPath("$.data.summaryText", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.keyPoints", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.actionItems", hasSize(greaterThan(0))))
                .andExpect(jsonPath("$.data.actionItems[0].assignee", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.chapters[0].title", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.chapters[0].startTime", notNullValue()));
    }

    @Test
    @Order(4)
    void generateSummary_ShortAndBullets_Success() throws Exception {
        GenerateSummaryRequest req = GenerateSummaryRequest.builder()
                .summaryLength("short")
                .summaryFormat("bullets")
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/" + job1Id + "/summary")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summaryText", startsWith("• ")));
    }

    @Test
    @Order(5)
    void generateSummary_ExecutiveFormat_Success() throws Exception {
        GenerateSummaryRequest req = GenerateSummaryRequest.builder()
                .summaryLength("medium")
                .summaryFormat("executive")
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/" + job1Id + "/summary")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.summaryText", containsString("Executive Brief:")));
    }

    @Test
    @Order(6)
    void getSummary_UnauthorizedUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/summary")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(7)
    void deleteSummary_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/transcriptions/" + job1Id + "/summary")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Confirm 404
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/summary")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isNotFound());
    }
}
