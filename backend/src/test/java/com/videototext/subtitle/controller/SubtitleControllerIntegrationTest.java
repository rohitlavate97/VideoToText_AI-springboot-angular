package com.videototext.subtitle.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.export.repository.ExportRepository;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.storage.service.StorageService;
import com.videototext.subtitle.dto.GenerateSubtitleRequest;
import com.videototext.subtitle.repository.SubtitleRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SubtitleControllerIntegrationTest {

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
    private SubtitleRepository subtitleRepository;

    @Autowired
    private ExportRepository exportRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private StorageService storageService;

    private static String tokenUser1;
    private static String tokenUser2;
    private static UUID job1Id;
    private static UUID createdSubtitleId;
    private static boolean initialized = false;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            // Register User 1
            RegisterRequest req1 = RegisterRequest.builder()
                    .email("subtitleuser1@example.com")
                    .password("Password123!")
                    .firstName("Subtitle")
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
                    .email("subtitleuser2@example.com")
                    .password("Password123!")
                    .firstName("Other")
                    .lastName("User")
                    .build();

            MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                    .path("data").path("accessToken").asText();

            User user1 = userRepository.findByEmail("subtitleuser1@example.com").orElseThrow();

            Video video = Video.builder()
                    .user(user1)
                    .originalFilename("test.mp4")
                    .storageKey("videos/" + user1.getId() + "/test.mp4")
                    .contentType("video/mp4")
                    .fileSizeBytes(1024L)
                    .durationSeconds(120)
                    .status("UPLOADED")
                    .build();
            video = videoRepository.save(video);

            TranscriptionJob job = TranscriptionJob.builder()
                    .video(video)
                    .user(user1)
                    .status("COMPLETED")
                    .sourceLanguage("en")
                    .build();
            job = jobRepository.save(job);
            job1Id = job.getId();

            // Create speakers
            Speaker spk1 = Speaker.builder()
                    .job(job)
                    .speakerLabel("SPEAKER_00")
                    .displayName("Alice")
                    .build();
            spk1 = speakerRepository.save(spk1);

            Speaker spk2 = Speaker.builder()
                    .job(job)
                    .speakerLabel("SPEAKER_01")
                    .displayName("Bob")
                    .build();
            spk2 = speakerRepository.save(spk2);

            // Create segments
            TranscriptSegment seg1 = TranscriptSegment.builder()
                    .job(job)
                    .segmentIndex(0)
                    .startTime(1.0f)
                    .endTime(4.5f)
                    .text("Welcome to the VideoToText AI subtitle demonstration.")
                    .speakerId(spk1.getId())
                    .confidence(0.95f)
                    .build();
            segmentRepository.save(seg1);

            TranscriptSegment seg2 = TranscriptSegment.builder()
                    .job(job)
                    .segmentIndex(1)
                    .startTime(5.0f)
                    .endTime(8.2f)
                    .text("We support SRT, WebVTT, plain text, and structured JSON.")
                    .speakerId(spk2.getId())
                    .confidence(0.92f)
                    .build();
            segmentRepository.save(seg2);

            initialized = true;
        }
    }

    @Test
    @Order(1)
    void generateSubtitle_SRT_Default_Success() throws Exception {
        GenerateSubtitleRequest request = GenerateSubtitleRequest.builder()
                .format("SRT")
                .language("en")
                .includeSpeakers(false)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/transcriptions/{jobId}/subtitles", job1Id)
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.format", is("SRT")))
                .andExpect(jsonPath("$.data.language", is("en")))
                .andExpect(jsonPath("$.data.includesSpeakers", is(false)))
                .andExpect(jsonPath("$.data.content", containsString("00:00:01,000 --> 00:00:04,500")))
                .andExpect(jsonPath("$.data.content", containsString("Welcome to the VideoToText AI subtitle demonstration.")))
                .andReturn();

        createdSubtitleId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("data").path("id").asText()
        );
    }

    @Test
    @Order(2)
    void generateSubtitle_VTT_WithSpeakers_Success() throws Exception {
        GenerateSubtitleRequest request = GenerateSubtitleRequest.builder()
                .format("VTT")
                .language("en")
                .includeSpeakers(true)
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/{jobId}/subtitles", job1Id)
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.format", is("VTT")))
                .andExpect(jsonPath("$.data.includesSpeakers", is(true)))
                .andExpect(jsonPath("$.data.content", startsWith("WEBVTT")))
                .andExpect(jsonPath("$.data.content", containsString("<v Alice>Welcome to the VideoToText AI subtitle demonstration.")))
                .andExpect(jsonPath("$.data.content", containsString("<v Bob>We support SRT, WebVTT, plain text, and structured JSON.")));
    }

    @Test
    @Order(3)
    void getSubtitles_List_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/{jobId}/subtitles", job1Id)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(2))));
    }

    @Test
    @Order(4)
    void downloadSubtitle_ByQueryParams_SRT_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/{jobId}/subtitles/download", job1Id)
                        .param("format", "SRT")
                        .param("language", "en")
                        .param("includeSpeakers", "false")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("transcription_")))
                .andExpect(header().string("Content-Type", startsWith("application/x-subrip")));
    }

    @Test
    @Order(5)
    void downloadSubtitle_ById_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/{jobId}/subtitles/{subtitleId}/download", job1Id, createdSubtitleId)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("subtitle_")));
    }

    @Test
    @Order(6)
    void exportTranscript_TXT_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/{jobId}/export", job1Id)
                        .param("format", "TXT")
                        .param("language", "en")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("text/plain")))
                .andExpect(header().string("Content-Disposition", containsString("export_")));
    }

    @Test
    @Order(7)
    void exportTranscript_JSON_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/{jobId}/export", job1Id)
                        .param("format", "JSON")
                        .param("language", "en")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("application/json")))
                .andExpect(header().string("Content-Disposition", containsString("export_")));
    }

    @Test
    @Order(8)
    void getExports_List_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/{jobId}/exports", job1Id)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @Order(9)
    void getSubtitles_UnauthorizedUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/{jobId}/subtitles", job1Id)
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    @Order(10)
    void deleteSubtitle_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/transcriptions/{jobId}/subtitles/{subtitleId}", job1Id, createdSubtitleId)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("deleted successfully")));
    }
}
