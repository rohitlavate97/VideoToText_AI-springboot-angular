package com.videototext.common.controller;

import com.videototext.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health & System", description = "System health check and runtime status endpoints")
public class HealthController {

    @Value("${spring.application.name:videototext-backend}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    @Operation(summary = "Liveness and Readiness probe", description = "Checks whether the backend API is live and responsive")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("application", applicationName);
        health.put("environment", activeProfile);
        health.put("timestamp", Instant.now());
        return ResponseEntity.ok(ApiResponse.success(health, "Service is healthy and operational"));
    }

    @Operation(summary = "System Runtime Metrics", description = "Returns JVM memory, processors, and runtime details")
    @GetMapping("/system")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStatus() {
        Runtime runtime = Runtime.getRuntime();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

        Map<String, Object> memoryInfo = new LinkedHashMap<>();
        memoryInfo.put("maxMemoryMb", runtime.maxMemory() / (1024 * 1024));
        memoryInfo.put("totalMemoryMb", runtime.totalMemory() / (1024 * 1024));
        memoryInfo.put("freeMemoryMb", runtime.freeMemory() / (1024 * 1024));
        memoryInfo.put("usedMemoryMb", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        memoryInfo.put("heapUsageMb", memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));

        Map<String, Object> systemStatus = new LinkedHashMap<>();
        systemStatus.put("application", applicationName);
        systemStatus.put("javaVersion", System.getProperty("java.version"));
        systemStatus.put("javaVendor", System.getProperty("java.vendor"));
        systemStatus.put("osName", System.getProperty("os.name"));
        systemStatus.put("osArchitecture", System.getProperty("os.arch"));
        systemStatus.put("availableProcessors", runtime.availableProcessors());
        systemStatus.put("memory", memoryInfo);
        systemStatus.put("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
        systemStatus.put("timestamp", Instant.now());

        return ResponseEntity.ok(ApiResponse.success(systemStatus, "System metrics retrieved successfully"));
    }
}
