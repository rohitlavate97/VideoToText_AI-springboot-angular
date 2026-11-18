package com.videototext.transcription.controller;

import com.videototext.common.dto.ApiResponse;
import com.videototext.security.audit.SecurityAuditService;
import com.videototext.transcription.dto.DeadLetterRecordDto;
import com.videototext.transcription.service.DeadLetterConsumer;
import com.videototext.transcription.service.JobRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reliability")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Reliability & Observability", description = "Monitor dead letter queues, inspect security audit events, and trigger stuck job recovery")
public class ReliabilityController {

    private final DeadLetterConsumer deadLetterConsumer;
    private final JobRecoveryService jobRecoveryService;
    private final SecurityAuditService securityAuditService;

    @Operation(summary = "Get recent dead letters", description = "Returns recent dead-letter events captured from the DLQ")
    @GetMapping("/dead-letters")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<DeadLetterRecordDto>>> getDeadLetters() {
        List<DeadLetterRecordDto> deadLetters = deadLetterConsumer.getRecentDeadLetters();
        return ResponseEntity.ok(ApiResponse.success(deadLetters, "Retrieved recent dead letters"));
    }

    @Operation(summary = "Trigger stuck job sweep", description = "Manually triggers background sweep of stuck jobs")
    @PostMapping("/sweep")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerSweep() {
        int recovered = jobRecoveryService.sweepStuckJobs();
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("recoveredCount", recovered, "status", "SUCCESS"),
                "Job recovery sweep executed successfully"
        ));
    }

    @Operation(summary = "Get recent security audit events", description = "Returns recent security and reliability audit events")
    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<SecurityAuditService.SecurityAuditEvent>>> getAuditLogs() {
        List<SecurityAuditService.SecurityAuditEvent> logs = securityAuditService.getRecentAuditLogs();
        return ResponseEntity.ok(ApiResponse.success(logs, "Retrieved recent security audit logs"));
    }
}
