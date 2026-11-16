package com.videototext.video.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.RegisterRequest;
import com.videototext.storage.service.CompletedPartDto;
import com.videototext.storage.service.StorageService;
import com.videototext.video.dto.InitiateUploadRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VideoControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private StorageService storageService;

    private static String tokenUser1;
    private static String tokenUser2;
    private static String uploadedVideoId;
    private static boolean initialized = false;

    // Helper: Valid MP4 header with 'ftyp' at offset 4 and trailing dummy payload
    private static byte[] createValidMp4Bytes() {
        byte[] bytes = new byte[128];
        // 00 00 00 18 'f' 't' 'y' 'p' 'i' 's' 'o' 'm'
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
                    .email("videotester1@example.com")
                    .password("Password123!")
                    .firstName("Video")
                    .lastName("Tester1")
                    .build();

            MvcResult res1 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req1)))
                    .andExpect(status().isCreated())
                    .andReturn();
            tokenUser1 = objectMapper.readTree(res1.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            // Register User 2
            RegisterRequest req2 = RegisterRequest.builder()
                    .email("videotester2@example.com")
                    .password("Password123!")
                    .firstName("Video")
                    .lastName("Tester2")
                    .build();

            MvcResult res2 = mockMvc.perform(post("/api/v1/auth/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req2)))
                    .andExpect(status().isCreated())
                    .andReturn();
            tokenUser2 = objectMapper.readTree(res2.getResponse().getContentAsString())
                    .get("data").get("accessToken").asText();

            initialized = true;
        }
    }

    @Test
    @Order(1)
    void shouldUploadValidMp4VideoSuccessfully() throws Exception {
        byte[] mp4Content = createValidMp4Bytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "sample-video.mp4",
                "video/mp4",
                mp4Content
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.id", notNullValue()))
                .andExpect(jsonPath("$.data.originalFilename", equalTo("sample-video.mp4")))
                .andExpect(jsonPath("$.data.status", equalTo("UPLOADED")))
                .andExpect(jsonPath("$.data.fileSizeBytes", equalTo(128)))
                .andReturn();

        uploadedVideoId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("id").asText();
    }

    @Test
    @Order(2)
    void shouldRejectUploadWithInvalidExtension() throws Exception {
        MockMultipartFile badFile = new MockMultipartFile(
                "file",
                "malicious-script.exe",
                "application/octet-stream",
                "binary content".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(badFile)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.error.code", equalTo("UNSUPPORTED_FORMAT")));
    }

    @Test
    @Order(3)
    void shouldRejectUploadWithInvalidMagicBytesSignature() throws Exception {
        // mp4 extension, but fake content without 'ftyp'
        MockMultipartFile fakeVideo = new MockMultipartFile(
                "file",
                "fake-video.mp4",
                "video/mp4",
                "This is not a video file header at all!".getBytes()
        );

        mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(fakeVideo)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.error.code", equalTo("UNSUPPORTED_FORMAT")));
    }

    @Test
    @Order(4)
    void shouldRejectUploadWhenUnauthenticated() throws Exception {
        byte[] mp4Content = createValidMp4Bytes();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "unauthorized.mp4",
                "video/mp4",
                mp4Content
        );

        mockMvc.perform(multipart("/api/v1/videos/upload")
                        .file(file))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void shouldListUploadedVideosForOwner() throws Exception {
        mockMvc.perform(get("/api/v1/videos")
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data.content[0].originalFilename", equalTo("sample-video.mp4")));
    }

    @Test
    @Order(6)
    void shouldGetVideoDetailsById() throws Exception {
        mockMvc.perform(get("/api/v1/videos/" + uploadedVideoId)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.id", equalTo(uploadedVideoId)))
                .andExpect(jsonPath("$.data.originalFilename", equalTo("sample-video.mp4")))
                .andExpect(jsonPath("$.data.downloadUrl", notNullValue()));
    }

    @Test
    @Order(7)
    void shouldForbidAccessToOtherUsersVideo() throws Exception {
        // User 2 attempts to access User 1's uploaded video
        mockMvc.perform(get("/api/v1/videos/" + uploadedVideoId)
                        .header("Authorization", "Bearer " + tokenUser2))
                .andExpect(status().isNotFound()); // ResourceNotFound for security isolation
    }

    @Test
    @Order(8)
    void shouldInitiateMultipartUploadSession() throws Exception {
        InitiateUploadRequest request = InitiateUploadRequest.builder()
                .filename("large-podcast.mp4")
                .fileSizeBytes(500L * 1024 * 1024) // 500 MB
                .contentType("video/mp4")
                .totalParts(10)
                .build();

        mockMvc.perform(post("/api/v1/videos/upload/initiate")
                        .header("Authorization", "Bearer " + tokenUser1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.uploadSessionId", notNullValue()))
                .andExpect(jsonPath("$.data.partSize", notNullValue()))
                .andExpect(jsonPath("$.data.totalParts", equalTo(10)));
    }

    @Test
    @Order(9)
    void shouldDeleteVideoSuccessfully() throws Exception {
        // Owner deletes their video
        mockMvc.perform(delete("/api/v1/videos/" + uploadedVideoId)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isNoContent());

        // Subsequent get returns 404
        mockMvc.perform(get("/api/v1/videos/" + uploadedVideoId)
                        .header("Authorization", "Bearer " + tokenUser1))
                .andExpect(status().isNotFound());
    }
}
