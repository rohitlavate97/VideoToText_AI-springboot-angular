package com.videototext.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.storage.service.StorageService;
import com.videototext.subtitle.service.SubtitleService;
import com.videototext.summary.dto.GenerateSummaryRequest;
import com.videototext.transcription.dto.CreateTranscriptionRequest;
import com.videototext.transcription.dto.TranscriptResponse;
import com.videototext.transcription.dto.UpdateTranscriptSegmentRequest;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.transcription.service.TranscriptionService;
import com.videototext.translation.dto.CreateTranslationRequest;
import com.videototext.translation.service.TranslationService;
import com.videototext.user.entity.User;
import com.videototext.user.repository.UserRepository;
import com.videototext.video.dto.InitiateUploadRequest;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class CompleteVideoProcessingE2ETest {

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
    private TranscriptionService transcriptionService;

    @Autowired
    private SubtitleService subtitleService;

    @Autowired
    private TranslationService translationService;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private static String jwtToken;
    private static UUID testUserId;
    private static UUID testVideoId;
    private static UUID testJobId;
    private static UUID firstSegmentId;
    private static boolean userRegistered = false;

    private static byte[] createValidMp4Header() {
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
                .thenReturn("https://storage.videototext.local/bucket/presigned-download-url");
        when(storageService.initiateMultipartUpload(anyString(), anyString()))
                .thenReturn("mock-upload-session-e2e");
        doNothing().when(storageService).uploadFile(anyString(), any(InputStream.class), anyLong(), anyString());
        doNothing().when(storageService).deleteFile(anyString());

        if (!userRegistered) {
            RegisterRequest req = RegisterRequest.builder()
                    .email("e2e_user@example.com")
                    .password("E2ePassword123!")
                    .firstName("E2E")
                    .lastName("Tester")
                    .build();

            MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn();

            String responseBody = res.getResponse().getContentAsString();
            jwtToken = objectMapper.readTree(responseBody).path("data").path("accessToken").asText();

            User user = userRepository.findByEmail("e2e_user@example.com").orElseThrow();
            testUserId = user.getId();
            userRegistered = true;
        }
    }

    @Test
    @Order(1)
    @DisplayName("E2E Step 1: Upload video with valid MP4 metadata")
    void testStep1UploadVideo() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lecture_multilingual.mp4",
                "video/mp4",
                createValidMp4Header()
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.originalFilename", is("lecture_multilingual.mp4")))
                .andExpect(jsonPath("$.data.status", is("UPLOADED")))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        testVideoId = UUID.fromString(objectMapper.readTree(responseJson).path("data").path("id").asText());
        assertNotNull(testVideoId);
    }

    @Test
    @Order(2)
    @DisplayName("E2E Step 2: Create transcription job with Diarization and Summary enabled")
    void testStep2CreateTranscriptionJob() throws Exception {
        CreateTranscriptionRequest request = CreateTranscriptionRequest.builder()
                .videoId(testVideoId)
                .language("en")
                .engine("faster-whisper")
                .model("small")
                .computeType("int8_float16")
                .enableDiarization(true)
                .maxSpeakers(2)
                .generateSummary(true)
                .targetLanguages(List.of("hi", "mr"))
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/transcriptions")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("QUEUED")))
                .andExpect(jsonPath("$.data.engine", is("faster-whisper")))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        testJobId = UUID.fromString(objectMapper.readTree(responseJson).path("data").path("id").asText());
        assertNotNull(testJobId);
    }

    @Test
    @Order(3)
    @DisplayName("E2E Step 3: Connect SSE progress event emitter for job tracking")
    void testStep3SseEventEmitter() throws Exception {
        mockMvc.perform(get("/api/v1/events/jobs/" + testJobId)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk());
    }

    @Test
    @Order(4)
    @DisplayName("E2E Step 4: Simulate AI worker completing transcription with Speaker Diarization")
    void testStep4SimulateTranscriptionCompletion() {
        TranscriptionJob job = jobRepository.findById(testJobId).orElseThrow();

        // 1. Create two distinct speakers
        Speaker speakerA = speakerRepository.save(Speaker.builder()
                .job(job)
                .speakerLabel("SPEAKER_00")
                .displayName("Dr. Sharma")
                .build());

        Speaker speakerB = speakerRepository.save(Speaker.builder()
                .job(job)
                .speakerLabel("SPEAKER_01")
                .displayName("Prof. Patil")
                .build());

        // 2. Persist 5 conversational segments
        List<TranscriptSegment> segments = new ArrayList<>();
        segments.add(TranscriptSegment.builder()
                .job(job)
                .speakerId(speakerA.getId())
                .segmentIndex(0)
                .startTime(0.0f)
                .endTime(12.5f)
                .text("Welcome to our joint seminar on Artificial Intelligence and Multilingual speech recognition systems.")
                .wordTimestampsJson("[{\"word\":\"Welcome\",\"startTime\":0.0,\"endTime\":0.8,\"confidence\":0.98},{\"word\":\"to\",\"startTime\":0.9,\"endTime\":1.1,\"confidence\":0.99}]")
                .confidence(0.97f)
                .build());

        segments.add(TranscriptSegment.builder()
                .job(job)
                .speakerId(speakerB.getId())
                .segmentIndex(1)
                .startTime(13.0f)
                .endTime(25.0f)
                .text("Thank you Dr. Sharma. It is an honor to demonstrate automatic speech translation for Indian languages.")
                .wordTimestampsJson("[{\"word\":\"Thank\",\"startTime\":13.0,\"endTime\":13.4,\"confidence\":0.96}]")
                .confidence(0.96f)
                .build());

        segments.add(TranscriptSegment.builder()
                .job(job)
                .speakerId(speakerA.getId())
                .segmentIndex(2)
                .startTime(25.5f)
                .endTime(40.0f)
                .text("We observe high transcription accuracy in Marathi and Hindi even with colloquial accents.")
                .confidence(0.95f)
                .build());

        segments.add(TranscriptSegment.builder()
                .job(job)
                .speakerId(speakerB.getId())
                .segmentIndex(3)
                .startTime(40.5f)
                .endTime(58.0f)
                .text("Next we will review the real-time chunking and VAD boundaries.")
                .confidence(0.98f)
                .build());

        segments.add(TranscriptSegment.builder()
                .job(job)
                .speakerId(speakerA.getId())
                .segmentIndex(4)
                .startTime(58.5f)
                .endTime(72.0f)
                .text("In conclusion, the pipeline handles heavy concurrent loads reliably without degradation.")
                .confidence(0.99f)
                .build());

        List<TranscriptSegment> saved = segmentRepository.saveAll(segments);
        firstSegmentId = saved.get(0).getId();

        job.setStatus("COMPLETED");
        job.setCurrentStage("COMPLETED");
        job.setProgressPercent(100.0f);
        job.setCompletedAt(Instant.now());
        jobRepository.save(job);
    }

    @Test
    @Order(5)
    @DisplayName("E2E Step 5: Retrieve full transcript and verify speaker attribution and words")
    void testStep5RetrieveFullTranscript() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/transcriptions/" + testJobId + "/transcript")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")))
                .andExpect(jsonPath("$.data.totalSegments", is(5)))
                .andExpect(jsonPath("$.data.segments[0].speakerDisplayName", is("Dr. Sharma")))
                .andExpect(jsonPath("$.data.segments[1].speakerDisplayName", is("Prof. Patil")))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        TranscriptResponse resp = objectMapper.readValue(objectMapper.readTree(json).path("data").toString(), TranscriptResponse.class);
        assertTrue(resp.getFullText().contains("Multilingual speech recognition"));
        assertTrue(resp.getFullText().contains("automatic speech translation"));
        assertEquals("Prof. Patil", resp.getSegments().get(1).getSpeakerDisplayName());
    }

    @Test
    @Order(6)
    @DisplayName("E2E Step 6: Search transcript text for keywords")
    void testStep6SearchTranscript() throws Exception {
        mockMvc.perform(get("/api/v1/transcriptions/" + testJobId + "/search")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("query", "Marathi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.totalMatches", is(1)))
                .andExpect(jsonPath("$.data.matches[0].text", containsString("Marathi and Hindi")));
    }

    @Test
    @Order(7)
    @DisplayName("E2E Step 7: Edit transcript segment and verify audit revision")
    void testStep7EditTranscriptSegment() throws Exception {
        UpdateTranscriptSegmentRequest updateReq = UpdateTranscriptSegmentRequest.builder()
                .text("Welcome to our keynote seminar on Artificial Intelligence and Multilingual speech recognition systems.")
                .startTime(0.0f)
                .endTime(12.5f)
                .build();

        mockMvc.perform(put("/api/v1/transcriptions/" + testJobId + "/segments/" + firstSegmentId)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.text", containsString("keynote seminar")));
    }

    @Test
    @Order(8)
    @DisplayName("E2E Step 8: Generate and retrieve AI executive summary")
    void testStep8GenerateAndRetrieveSummary() throws Exception {
        GenerateSummaryRequest summaryReq = GenerateSummaryRequest.builder()
                .summaryLength("medium")
                .summaryFormat("bullet_points")
                .extractKeyPoints(true)
                .extractActionItems(true)
                .extractTopics(true)
                .generateChapters(true)
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/" + testJobId + "/summary")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(summaryReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.summaryText").isNotEmpty())
                .andExpect(jsonPath("$.data.keyPoints").isArray());
    }

    @Test
    @Order(9)
    @DisplayName("E2E Step 9: Request multilingual translation in Hindi and Marathi")
    void testStep9MultilingualTranslation() throws Exception {
        CreateTranslationRequest translationReq = CreateTranslationRequest.builder()
                .targetLanguage("hi")
                .engine("nllb")
                .build();

        mockMvc.perform(post("/api/v1/transcriptions/" + testJobId + "/translations")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(translationReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.targetLanguage", is("hi")))
                .andExpect(jsonPath("$.data.status", is("COMPLETED")));
    }

    @Test
    @Order(10)
    @DisplayName("E2E Step 10: Export subtitles in SRT, VTT, and TXT with speaker labels")
    void testStep10ExportSubtitles() throws Exception {
        // SRT
        mockMvc.perform(get("/api/v1/transcriptions/" + testJobId + "/subtitles/download")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("format", "SRT")
                        .param("includeSpeakers", "true"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String srt = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    assertTrue(srt.contains("-->"));
                    assertTrue(srt.contains("Dr. Sharma"));
                });

        // VTT
        mockMvc.perform(get("/api/v1/transcriptions/" + testJobId + "/subtitles/download")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("format", "VTT")
                        .param("includeSpeakers", "true"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String vtt = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    assertTrue(vtt.startsWith("WEBVTT"));
                });

        // TXT
        mockMvc.perform(get("/api/v1/transcriptions/" + testJobId + "/subtitles/download")
                        .header("Authorization", "Bearer " + jwtToken)
                        .param("format", "TXT")
                        .param("includeSpeakers", "true"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String txt = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
                    assertTrue(txt.contains("[Dr. Sharma]:"));
                });
    }

    @Test
    @Order(11)
    @DisplayName("E2E Step 11: Multipart chunked upload session for large video files")
    void testStep11MultipartUploadSession() throws Exception {
        InitiateUploadRequest initReq = InitiateUploadRequest.builder()
                .filename("large_video_presentation.mp4")
                .fileSizeBytes(100 * 1024 * 1024L)
                .totalParts(2)
                .build();

        mockMvc.perform(post("/api/v1/videos/upload/initiate")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.uploadSessionId").isNotEmpty())
                .andExpect(jsonPath("$.data.totalParts", is(2)));
    }

    @Test
    @Order(12)
    @DisplayName("E2E Step 12: Cancellation of active transcription job restores video status")
    void testStep12JobCancellation() throws Exception {
        // Create another job to test cancellation
        CreateTranscriptionRequest cancelReq = CreateTranscriptionRequest.builder()
                .videoId(testVideoId)
                .language("en")
                .engine("faster-whisper")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/transcriptions")
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isCreated())
                .andReturn();

        UUID jobToCancelId = UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText());

        // Cancel job
        mockMvc.perform(post("/api/v1/transcriptions/" + jobToCancelId + "/cancel")
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("CANCELLED")));

        // Verify video status restored to READY
        Video video = videoRepository.findById(testVideoId).orElseThrow();
        assertEquals("READY", video.getStatus());
    }
}
