package com.videototext.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.videototext.auth.dto.LoginRequest;
import com.videototext.auth.dto.RefreshTokenRequest;
import com.videototext.auth.dto.RegisterRequest;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
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
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String accessToken;
    private static String refreshToken;

    @Test
    @Order(1)
    void shouldRegisterNewUserSuccessfully() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .email("testuser@example.com")
                .password("Password123!")
                .firstName("Test")
                .lastName("User")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()))
                .andExpect(jsonPath("$.data.user.email", equalTo("testuser@example.com")))
                .andExpect(jsonPath("$.data.user.roles", hasItem("ROLE_USER")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        accessToken = objectMapper.readTree(responseBody).get("data").get("accessToken").asText();
        refreshToken = objectMapper.readTree(responseBody).get("data").get("refreshToken").asText();
    }

    @Test
    @Order(2)
    void shouldRejectDuplicateRegistration() throws Exception {
        RegisterRequest duplicate = RegisterRequest.builder()
                .email("testuser@example.com")
                .password("Password123!")
                .firstName("Duplicate")
                .lastName("User")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.error.code", equalTo("DUPLICATE_RESOURCE")));
    }

    @Test
    @Order(3)
    void shouldRejectRegistrationWithWeakPassword() throws Exception {
        RegisterRequest weak = RegisterRequest.builder()
                .email("weak@example.com")
                .password("simple") // No upper, no digit, no special, < 8 chars
                .firstName("Weak")
                .lastName("Password")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weak)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.error.code", equalTo("VALIDATION_ERROR")));
    }

    @Test
    @Order(4)
    void shouldLoginSuccessfullyWithValidCredentials() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .email("testuser@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()));
    }

    @Test
    @Order(5)
    void shouldRejectLoginWithIncorrectPassword() throws Exception {
        LoginRequest wrong = LoginRequest.builder()
                .email("testuser@example.com")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrong)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.error.code", equalTo("AUTHENTICATION_REQUIRED")));
    }

    @Test
    @Order(6)
    void shouldRefreshTokenSuccessfully() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(refreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.accessToken", notNullValue()))
                .andExpect(jsonPath("$.data.refreshToken", notNullValue()));
    }

    @Test
    @Order(7)
    void shouldAccessUserProfileWithValidBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.email", equalTo("testuser@example.com")))
                .andExpect(jsonPath("$.data.firstName", equalTo("Test")))
                .andExpect(jsonPath("$.data.preferences.defaultLanguage", equalTo("en")));
    }

    @Test
    @Order(8)
    void shouldRejectProfileAccessWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", equalTo(false)))
                .andExpect(jsonPath("$.error.code", equalTo("AUTHENTICATION_REQUIRED")));
    }
}
