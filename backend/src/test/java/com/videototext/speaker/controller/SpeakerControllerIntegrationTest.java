package com.videototext.speaker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.speaker.dto.UpdateSpeakerRequest;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.storage.service.StorageService;
import com.videototext.transcription.entity.TranscriptionJob;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SpeakerControllerIntegrationTest {

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
    private SpeakerRepository speakerRepository;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @MockitoBean
    private StorageService storageService;

    private static String tokenUser1;
    private static String tokenUser2;
    private static UUID job1Id;
    private static UUID speaker1Id;
    private static boolean initialized = false;

    @BeforeEach
    void setUp() throws Exception {
        if (!initialized) {
            // Register User 1
            RegisterRequest req1 = RegisterRequest.builder()
                    .email("speakertester1@example.com")
                    .password("Password123!")
                    .firstName("Speaker")
                    .lastName("Tester1")
                    .build();

            MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser1 = objectMapper.readTree(res1.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            User user1 = userRepository.findByEmail("speakertester1@example.com").orElseThrow();

            // Register User 2
            RegisterRequest req2 = RegisterRequest.builder()
                    .email("speakertester2@example.com")
                    .password("Password123!")
                    .firstName("Speaker")
                    .lastName("Tester2")
                    .build();

            MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andReturn();

            tokenUser2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            // Seed Video & Job
            Video v = Video.builder()
                    .user(user1)
                    .originalFilename("speaker_interview.mp4")
                    .storageKey("videos/" + user1.getId() + "/speaker_interview.mp4")
                    .contentType("video/mp4")
                    .fileSizeBytes(4000000L)
                    .durationSeconds(180)
                    .status("READY")
                    .build();
            v = videoRepository.save(v);

            TranscriptionJob job = TranscriptionJob.builder()
                    .video(v)
                    .user(user1)
                    .status("COMPLETED")
                    .currentStage("COMPLETED")
                    .progressPercent(100.0f)
                    .build();
            job = jobRepository.save(job);
            job1Id = job.getId();

            // Seed Speakers
            Speaker s1 = Speaker.builder()
                    .job(job)
                    .speakerLabel("SPEAKER_00")
                    .displayName("Host")
                    .totalSpeakingTimeMs(75000)
                    .segmentCount(14)
                    .build();
            s1 = speakerRepository.save(s1);
            speaker1Id = s1.getId();

            Speaker s2 = Speaker.builder()
                    .job(job)
                    .speakerLabel("SPEAKER_01")
                    .displayName("Guest")
                    .totalSpeakingTimeMs(105000)
                    .segmentCount(18)
                    .build();
            speakerRepository.save(s2);

            initialized = true;
        }
    }

    @Test
    @Order(1)
    void getSpeakers_Success() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/speakers")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].speakerLabel").value("SPEAKER_00"))
                .andExpect(jsonPath("$.data[0].displayName").value("Host"))
                .andExpect(jsonPath("$.data[1].speakerLabel").value("SPEAKER_01"))
                .andExpect(jsonPath("$.data[1].displayName").value("Guest"));
    }

    @Test
    @Order(2)
    void updateSpeaker_Success() throws Exception {
        UpdateSpeakerRequest request = UpdateSpeakerRequest.builder()
                .displayName("Dr. Alice Smith")
                .build();

        mockMvc.perform(put("/api/v1/transcriptions/" + job1Id + "/speakers/" + speaker1Id)
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(speaker1Id.toString()))
                .andExpect(jsonPath("$.data.displayName").value("Dr. Alice Smith"));
    }

    @Test
    @Order(3)
    void getSpeakers_UnauthorizedUser_Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + job1Id + "/speakers")
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(4)
    void updateSpeaker_UnauthorizedUser_Forbidden() throws Exception {
        UpdateSpeakerRequest request = UpdateSpeakerRequest.builder()
                .displayName("Hacked Name")
                .build();

        mockMvc.perform(put("/api/v1/transcriptions/" + job1Id + "/speakers/" + speaker1Id)
                        .header("Authorization", "Bearer " + tokenUser2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @Order(5)
    void updateSpeaker_BlankDisplayName_BadRequest() throws Exception {
        UpdateSpeakerRequest request = UpdateSpeakerRequest.builder()
                .displayName("")
                .build();

        mockMvc.perform(put("/api/v1/transcriptions/" + job1Id + "/speakers/" + speaker1Id)
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @Order(6)
    void updateSpeaker_NonExistentSpeaker_NotFound() throws Exception {
        UpdateSpeakerRequest request = UpdateSpeakerRequest.builder()
                .displayName("Valid Name")
                .build();

        mockMvc.perform(put("/api/v1/transcriptions/" + job1Id + "/speakers/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }
}
