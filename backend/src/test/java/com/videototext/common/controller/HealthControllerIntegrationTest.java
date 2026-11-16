package com.videototext.common.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnHealthyStatusFromHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.message", equalTo("Service is healthy and operational")))
                .andExpect(jsonPath("$.data.status", equalTo("UP")))
                .andExpect(jsonPath("$.data.application", equalTo("videototext-backend")))
                .andExpect(jsonPath("$.timestamp", notNullValue()));
    }

    @Test
    void shouldReturnSystemMetricsFromSystemEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/health/system")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.data.javaVersion", notNullValue()))
                .andExpect(jsonPath("$.data.memory", notNullValue()))
                .andExpect(jsonPath("$.data.availableProcessors", notNullValue()));
    }
}
