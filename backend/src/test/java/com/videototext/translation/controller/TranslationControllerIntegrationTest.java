package com.videototext.translation.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.storage.service.StorageService;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.translation.dto.CreateTranslationRequest;
import com.videototext.translation.repository.TranslationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TranslationControllerIntegrationTest {

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
    private TranslationRepository translationRepository;

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
                    .email("translationuser1@example.com")
                    .password("Password123!")
                    .firstName("Trans")
                    .lastName("Tester")
                    .build();

            MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser1 = objectMapper.readTree(res1.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            User user1 = userRepository.findByEmail("translationuser1@example.com").orElseThrow();

            // Register User 2
            RegisterRequest req2 = RegisterRequest.builder()
                    .email("translationuser2@example.com")
                    .password("Password123!")
                    .firstName("Other")
                    .lastName("Tester")
                    .build();

            MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            // Seed Video for User 1
            Video video = Video.builder()
                    .user(user1)
                    .originalFilename("multilingual_test.mp4")
                    .storageKey("videos/" + user1.getId() + "/multilingual_test.mp4")
                    .contentType("video/mp4")
                    .fileSizeBytes(1234567L)
                    .durationSeconds(60)
                    .status("UPLOADED")
                    .build();
            video = videoRepository.save(video);

            // Seed Completed Transcription Job for User 1
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
                    .displayName("Alice")
                    .build();
            spk1 = speakerRepository.save(spk1);

            Speaker spk2 = Speaker.builder()
                    .job(job)
                    .speakerLabel("SPEAKER_01")
                    .displayName("Bob")
                    .build();
            spk2 = speakerRepository.save(spk2);

            // Seed Transcript Segments
            TranscriptSegment seg1 = TranscriptSegment.builder()
                    .job(job)
                    .speakerId(spk1.getId())
                    .segmentIndex(0)
                    .startTime(0.0f)
                    .endTime(3.5f)
                    .text("Welcome to the video presentation on speech recognition.")
                    .confidence(0.98f)
                    .build();
            segmentRepository.save(seg1);

            TranscriptSegment seg2 = TranscriptSegment.builder()
                    .job(job)
                    .speakerId(spk2.getId())
                    .segmentIndex(1)
                    .startTime(3.5f)
                    .endTime(7.2f)
                    .text("Today we discuss deep learning.")
                    .confidence(0.95f)
                    .build();
            segmentRepository.save(seg2);

            initialized = true;
        }
    }

    @Test
    @Order(1)
    void getTranslations_EmptyInitial_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/translations")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @Order(2)
    void requestTranslation_Hindi_Success() throws Exception {
        CreateTranslationRequest req = CreateTranslationRequest.builder()
                .targetLanguage("hi")
                .engine("nllb")
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/" + job1Id + "/translations")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetLanguage").value("hi"))
                .andExpect(jsonPath("$.data.sourceLanguage").value("en"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.segments", hasSize(2)))
                .andExpect(jsonPath("$.data.segments[0].startTime").value(0.0))
                .andExpect(jsonPath("$.data.segments[0].endTime").value(3.5))
                .andExpect(jsonPath("$.data.segments[0].speakerLabel").value("Alice"))
                .andExpect(jsonPath("$.data.segments[0].translatedText", not(emptyOrNullString())))
                .andExpect(jsonPath("$.data.segments[1].startTime").value(3.5))
                .andExpect(jsonPath("$.data.segments[1].endTime").value(7.2))
                .andExpect(jsonPath("$.data.segments[1].speakerLabel").value("Bob"));
    }

    @Test
    @Order(3)
    void getTranslation_Hindi_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/translations/hi")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetLanguage").value("hi"))
                .andExpect(jsonPath("$.data.segments", hasSize(2)))
                .andExpect(jsonPath("$.data.segments[0].speakerLabel").value("Alice"));
    }

    @Test
    @Order(4)
    void requestTranslation_Marathi_Success() throws Exception {
        CreateTranslationRequest req = CreateTranslationRequest.builder()
                .targetLanguage("mr")
                .engine("nllb")
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/" + job1Id + "/translations")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.targetLanguage").value("mr"))
                .andExpect(jsonPath("$.data.segments", hasSize(2)))
                .andExpect(jsonPath("$.data.segments[0].startTime").value(0.0))
                .andExpect(jsonPath("$.data.segments[1].startTime").value(3.5));
    }

    @Test
    @Order(5)
    void requestTranslation_DuplicateLanguage_Conflict() throws Exception {
        CreateTranslationRequest req = CreateTranslationRequest.builder()
                .targetLanguage("hi")
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/" + job1Id + "/translations")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @Order(6)
    void getTranslation_NotFound_Throws404() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/translations/es")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void getTranslations_UnauthorizedUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/translations")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isForbidden());
    }
}
