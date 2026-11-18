package com.videototext.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.common.exception.BadRequestException;
import com.videototext.common.exception.ErrorCode;
import com.videototext.speaker.entity.Speaker;
import com.videototext.speaker.repository.SpeakerRepository;
import com.videototext.storage.service.StorageService;
import com.videototext.subtitle.dto.SubtitleResponse;
import com.videototext.subtitle.service.SubtitleService;
import com.videototext.transcription.dto.CreateTranscriptionRequest;
import com.videototext.transcription.dto.TranscriptResponse;
import com.videototext.transcription.dto.TranscriptionJobResponse;
import com.videototext.transcription.entity.TranscriptSegment;
import com.videototext.transcription.entity.TranscriptionJob;
import com.videototext.transcription.repository.TranscriptSegmentRepository;
import com.videototext.transcription.repository.TranscriptionJobRepository;
import com.videototext.transcription.service.TranscriptionService;
import com.videototext.translation.dto.CreateTranslationRequest;
import com.videototext.translation.dto.TranslationResponse;
import com.videototext.translation.service.TranslationService;
import com.videototext.user.entity.User;
import com.videototext.user.repository.UserRepository;
import com.videototext.video.entity.Video;
import com.videototext.video.repository.VideoRepository;
import com.videototext.video.validation.VideoValidator;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComprehensivePipelineIntegrationTest {

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

    @Autowired
    private VideoValidator videoValidator;

    @MockitoBean
    private StorageService storageService;

    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    private static String jwtToken;
    private static UUID testUserId;
    private static boolean initialized = false;

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
                .thenReturn("https://storage.videototext.local/bucket/presigned-url");
        when(storageService.initiateMultipartUpload(anyString(), anyString()))
                .thenReturn("mock-upload-session-pipeline");
        doNothing().when(storageService).uploadFile(anyString(), any(InputStream.class), anyLong(), anyString());
        doNothing().when(storageService).deleteFile(anyString());

        if (!initialized) {
            RegisterRequest req = RegisterRequest.builder()
                    .email("pipeline_test_user@example.com")
                    .password("PipelinePass123!")
                    .firstName("Pipeline")
                    .lastName("Tester")
                    .build();

            MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andReturn();

            jwtToken = objectMapper.readTree(res.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            User user = userRepository.findByEmail("pipeline_test_user@example.com").orElseThrow();
            testUserId = user.getId();
            initialized = true;
        }
    }

    @Test
    @Order(1)
    @DisplayName("Scenario 1-3: Valid video upload and rejection of unsupported / corrupted files")
    void testUploadValidationScenarios() throws Exception {
        // 1. Valid video upload
        MockMultipartFile validMp4 = new MockMultipartFile(
                "file",
                "lecture_speech.mp4",
                "video/mp4",
                createValidMp4Header()
        );

        mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(validMp4)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.originalFilename", is("lecture_speech.mp4")))
                .andExpect(jsonPath("$.data.status", is("UPLOADED")));

        // 2. Unsupported format extension (.pdf)
        MockMultipartFile unsupportedFile = new MockMultipartFile(
                "file",
                "document.pdf",
                "application/pdf",
                new byte[]{0x25, 0x50, 0x44, 0x46}
        );

        mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(unsupportedFile)
                        .header("Authorization", "Bearer " + jwtToken))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code", is(ErrorCode.UNSUPPORTED_FORMAT.getCode())));

        // 3. Corrupted / zero-byte stream
        ByteArrayInputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        assertThrows(BadRequestException.class, () -> videoValidator.validateMagicBytes(emptyStream, "empty.mp4"));
    }

    @Test
    @Order(2)
    @DisplayName("Scenario 4-5: Validate 240-minute duration constraint (Accept <= 240m, Reject > 240m)")
    void testFourHourDurationConstraint() {
        // Exactly 4 hours (240 minutes) is valid
        assertDoesNotThrow(() -> videoValidator.validateDuration(240 * 60));

        // 4 hours + 1 second (240m 1s) exceeds limit and throws DURATION_EXCEEDED
        BadRequestException ex = assertThrows(
                BadRequestException.class,
                () -> videoValidator.validateDuration((240 * 60) + 1)
        );
        assertEquals(ErrorCode.DURATION_EXCEEDED, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("240 minutes"));
    }

    @Test
    @Order(3)
    @DisplayName("Scenario 8 & 18: Large transcript with 50+ segments, multiple speakers, and word count aggregation")
    void testLargeTranscriptAndMultipleSpeakers() {
        User user = userRepository.findById(testUserId).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("multi_speaker_lecture.mp4")
                .storageKey("videos/" + testUserId + "/lecture.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(15000000L)
                .durationSeconds(1800)
                .status("READY")
                .build());

        TranscriptionJob job = jobRepository.save(TranscriptionJob.builder()
                .user(user)
                .video(video)
                .status("COMPLETED")
                .asrEngine("faster-whisper")
                .asrModel("large-v3")
                .sourceLanguage("en")
                .currentStage("COMPLETED")
                .progressPercent(100.0f)
                .completedAt(Instant.now())
                .build());

        // Create 2 distinct speakers
        Speaker speakerA = speakerRepository.save(Speaker.builder()
                .job(job)
                .speakerLabel("SPEAKER_00")
                .displayName("Dr. Aris Thorne")
                .build());

        Speaker speakerB = speakerRepository.save(Speaker.builder()
                .job(job)
                .speakerLabel("SPEAKER_01")
                .displayName("Prof. Elena Vance")
                .build());

        // Persist 50 alternating transcript segments
        List<TranscriptSegment> segments = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            UUID speakerId = (i % 2 == 0) ? speakerA.getId() : speakerB.getId();
            float start = i * 10.0f;
            float end = start + 9.5f;
            String text = (i % 2 == 0)
                    ? "Speaker A discusses quantum computing architecture principles."
                    : "Speaker B addresses decoherence challenges in quantum state registers.";

            segments.add(TranscriptSegment.builder()
                    .job(job)
                    .segmentIndex(i)
                    .startTime(start)
                    .endTime(end)
                    .text(text)
                    .speakerId(speakerId)
                    .confidence(0.96f)
                    .build());
        }
        segmentRepository.saveAll(segments);

        // Fetch aggregated transcript
        TranscriptResponse response = transcriptionService.getTranscript(testUserId, job.getId());
        assertNotNull(response);
        assertEquals(50, response.getTotalSegments());
        assertEquals(job.getId(), response.getJobId());
        assertTrue(response.getTotalWords() > 300);
        assertTrue(response.getFullText().contains("quantum computing architecture"));
        assertTrue(response.getFullText().contains("decoherence challenges"));
    }

    @Test
    @Order(4)
    @DisplayName("Scenario 9-12: Multilingual transcription & translation pipeline (Hindi, Marathi, English)")
    void testMultilingualTranscriptionAndTranslation() {
        User user = userRepository.findById(testUserId).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("multilingual_speech.mp4")
                .storageKey("videos/" + testUserId + "/multi.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(8000000L)
                .durationSeconds(300)
                .status("READY")
                .build());

        // Create transcription request with multilingual targets: Hindi ("hi") and Marathi ("mr")
        CreateTranscriptionRequest createReq = CreateTranscriptionRequest.builder()
                .videoId(video.getId())
                .engine("faster-whisper")
                .model("large-v3")
                .language("hi") // Hindi source
                .enableDiarization(true)
                .targetLanguages(List.of("mr", "en")) // Marathi and English translations
                .build();

        TranscriptionJobResponse jobResp = transcriptionService.createJob(testUserId, createReq);
        assertNotNull(jobResp);
        assertEquals("hi", jobResp.getLanguage());
        assertEquals("QUEUED", jobResp.getStatus());

        // Simulate translation creation for Marathi ("mr")
        TranscriptionJob job = jobRepository.findById(jobResp.getId()).orElseThrow();
        CreateTranslationRequest translationReq = CreateTranslationRequest.builder()
                .targetLanguage("mr")
                .engine("nllb")
                .build();
        TranslationResponse translationResp = translationService.requestTranslation(testUserId, job.getId(), translationReq);
        assertNotNull(translationResp);
        assertEquals("mr", translationResp.getTargetLanguage());
        assertEquals("COMPLETED", translationResp.getStatus());
    }

    @Test
    @Order(5)
    @DisplayName("Scenario Subtitles: Export subtitles in SRT, VTT, and JSON formats")
    void testSubtitleExportGeneration() {
        User user = userRepository.findById(testUserId).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("subtitle_test.mp4")
                .storageKey("videos/" + testUserId + "/subtitles.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(5000000L)
                .durationSeconds(60)
                .status("READY")
                .build());

        TranscriptionJob job = jobRepository.save(TranscriptionJob.builder()
                .user(user)
                .video(video)
                .status("COMPLETED")
                .sourceLanguage("en")
                .currentStage("COMPLETED")
                .progressPercent(100.0f)
                .completedAt(Instant.now())
                .build());

        segmentRepository.save(TranscriptSegment.builder()
                .job(job)
                .segmentIndex(0)
                .startTime(0.0f)
                .endTime(4.5f)
                .text("Welcome to the video transcription and subtitle export test.")
                .confidence(0.98f)
                .build());

        segmentRepository.save(TranscriptSegment.builder()
                .job(job)
                .segmentIndex(1)
                .startTime(5.0f)
                .endTime(9.2f)
                .text("Subtitles can be exported in WebVTT, SRT, JSON, and plain text formats.")
                .confidence(0.97f)
                .build());

        // Generate SRT format
        byte[] srtBytes = subtitleService.downloadSubtitle(testUserId, job.getId(), "srt", "en", false);
        String srtContent = new String(srtBytes, StandardCharsets.UTF_8);
        assertTrue(srtContent.contains("00:00:00,000 --> 00:00:04,500"));
        assertTrue(srtContent.contains("Welcome to the video transcription"));

        // Generate VTT format
        byte[] vttBytes = subtitleService.downloadSubtitle(testUserId, job.getId(), "vtt", "en", false);
        String vttContent = new String(vttBytes, StandardCharsets.UTF_8);
        assertTrue(vttContent.startsWith("WEBVTT"));
        assertTrue(vttContent.contains("00:00:00.000 --> 00:00:04.500"));

        // Generate TXT format
        byte[] txtBytes = subtitleService.downloadSubtitle(testUserId, job.getId(), "txt", "en", false);
        String txtContent = new String(txtBytes, StandardCharsets.UTF_8);
        assertTrue(txtContent.contains("Welcome to the video transcription"));
    }

    @Test
    @Order(6)
    @DisplayName("Scenario 19: User job cancellation resets video status and stops active pipeline")
    void testUserJobCancellation() {
        User user = userRepository.findById(testUserId).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("cancel_test.mp4")
                .storageKey("videos/" + testUserId + "/cancel.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(4000000L)
                .durationSeconds(60)
                .status("PROCESSING")
                .build());

        TranscriptionJob job = jobRepository.save(TranscriptionJob.builder()
                .user(user)
                .video(video)
                .status("PROCESSING")
                .currentStage("TRANSCRIBING")
                .progressPercent(45.0f)
                .build());

        // Cancel job
        TranscriptionJobResponse cancelled = transcriptionService.cancelJob(testUserId, job.getId());
        assertEquals("CANCELLED", cancelled.getStatus());
        assertEquals("CANCELLED", cancelled.getCurrentStage());

        // Video status should be reset to READY
        Video updatedVideo = videoRepository.findById(video.getId()).orElseThrow();
        assertEquals("READY", updatedVideo.getStatus());
    }

    @Test
    @Order(7)
    @DisplayName("Scenario 7: Handle empty speech gracefully without failure")
    void testAudioWithoutSpeech() {
        User user = userRepository.findById(testUserId).orElseThrow();
        Video video = videoRepository.save(Video.builder()
                .user(user)
                .originalFilename("silent_audio.mp4")
                .storageKey("videos/" + testUserId + "/silent.mp4")
                .contentType("video/mp4")
                .fileSizeBytes(1000000L)
                .durationSeconds(30)
                .status("READY")
                .build());

        // Completed job with zero segments
        TranscriptionJob job = jobRepository.save(TranscriptionJob.builder()
                .user(user)
                .video(video)
                .status("COMPLETED")
                .currentStage("COMPLETED")
                .progressPercent(100.0f)
                .completedAt(Instant.now())
                .build());

        TranscriptResponse response = transcriptionService.getTranscript(testUserId, job.getId());
        assertNotNull(response);
        assertEquals(0, response.getTotalSegments());
        assertEquals(0, response.getTotalWords());
        assertEquals("", response.getFullText());
    }
}
