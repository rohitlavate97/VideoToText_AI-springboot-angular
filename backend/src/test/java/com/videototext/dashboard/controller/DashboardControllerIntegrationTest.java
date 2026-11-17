package com.videototext.dashboard.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.export.entity.Export;
import com.videototext.export.repository.ExportRepository;
import com.videototext.storage.service.StorageService;
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

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DashboardControllerIntegrationTest {

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
    private ExportRepository exportRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private StorageService storageService;

    private static String tokenUser1;
    private static String tokenUser2;
    private static boolean initialized = false;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            // Register User 1
            RegisterRequest req1 = RegisterRequest.builder()
                    .email("dashuser1@example.com")
                    .password("Password123!")
                    .firstName("Dashboard")
                    .lastName("Owner")
                    .build();

            MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser1 = objectMapper.readTree(res1.getResponse().getContentAsString())
                    .path("data").path("accessToken").asText();

            // Register User 2
            RegisterRequest req2 = RegisterRequest.builder()
                    .email("dashuser2@example.com")
                    .password("Password123!")
                    .firstName("Empty")
                    .lastName("User")
                    .build();

            MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                    .path("data").path("accessToken").asText();

            User user1 = userRepository.findByEmail("dashuser1@example.com").orElseThrow();

            // Video 1
            Video video1 = Video.builder()
                    .user(user1)
                    .originalFilename("sample_talk.mp4")
                    .storageKey("videos/" + user1.getId() + "/sample_talk.mp4")
                    .contentType("video/mp4")
                    .fileSizeBytes(5000000L)
                    .durationSeconds(150)
                    .status("READY")
                    .build();
            video1 = videoRepository.save(video1);

            // Job 1 (Completed)
            TranscriptionJob job1 = TranscriptionJob.builder()
                    .video(video1)
                    .user(user1)
                    .status("COMPLETED")
                    .sourceLanguage("en")
                    .build();
            job1 = jobRepository.save(job1);

            // Segments for Job 1
            TranscriptSegment seg1 = TranscriptSegment.builder()
                    .job(job1)
                    .segmentIndex(0)
                    .startTime(0.0f)
                    .endTime(5.0f)
                    .text("Hello world welcome to our analytics demo.")
                    .confidence(0.95f)
                    .build();
            segmentRepository.save(seg1);

            // Video 2
            Video video2 = Video.builder()
                    .user(user1)
                    .originalFilename("hindi_interview.mp4")
                    .storageKey("videos/" + user1.getId() + "/hindi_interview.mp4")
                    .contentType("video/mp4")
                    .fileSizeBytes(3000000L)
                    .durationSeconds(90)
                    .status("PROCESSING")
                    .build();
            video2 = videoRepository.save(video2);

            // Job 2 (Processing)
            TranscriptionJob job2 = TranscriptionJob.builder()
                    .video(video2)
                    .user(user1)
                    .status("PROCESSING")
                    .sourceLanguage("hi")
                    .build();
            jobRepository.save(job2);

            // Export for User 1
            Export export1 = Export.builder()
                    .job(job1)
                    .user(user1)
                    .exportType("SUBTITLE")
                    .format("SRT")
                    .language("en")
                    .storageKey("subtitles/export.srt")
                    .fileSizeBytes(512L)
                    .build();
            exportRepository.save(export1);

            initialized = true;
        }
    }

    @Test
    @Order(1)
    void getStats_UserWithJobs_ReturnsAccurateMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalVideos", is(2)))
                .andExpect(jsonPath("$.data.totalAudioDurationSeconds", is(240.0)))
                .andExpect(jsonPath("$.data.totalTranscribedWords", is(7)))
                .andExpect(jsonPath("$.data.totalExports", is(1)))
                .andExpect(jsonPath("$.data.statusCounts.COMPLETED", is(1)))
                .andExpect(jsonPath("$.data.statusCounts.PROCESSING", is(1)))
                .andExpect(jsonPath("$.data.languageDistribution.en", is(1)))
                .andExpect(jsonPath("$.data.languageDistribution.hi", is(1)))
                .andExpect(jsonPath("$.data.recentActivity", hasSize(2)))
                .andExpect(jsonPath("$.data.recentActivity[0].filename", notNullValue()));
    }

    @Test
    @Order(2)
    void getStats_EmptyUser_ReturnsZeros() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalVideos", is(0)))
                .andExpect(jsonPath("$.data.totalAudioDurationSeconds", is(0.0)))
                .andExpect(jsonPath("$.data.totalTranscribedWords", is(0)))
                .andExpect(jsonPath("$.data.totalExports", is(0)))
                .andExpect(jsonPath("$.data.recentActivity", hasSize(0)));
    }

    @Test
    @Order(3)
    void getStats_Unauthenticated_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }
}
