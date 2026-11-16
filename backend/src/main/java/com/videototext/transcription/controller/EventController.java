package com.videototext.transcription.controller;

import com.videototext.transcription.service.SseEmitterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Tag(name = "Real-time Event Streaming", description = "Server-Sent Events (SSE) for live transcription progress tracking")
public class EventController {

    private final SseEmitterService sseEmitterService;

    @Operation(summary = "Stream job progress events (SSE)", description = "Establishes a continuous SSE stream broadcasting real-time progress for a transcription job")
    @GetMapping(value = "/jobs/{jobId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamJobEvents(@PathVariable UUID jobId) {
        log.info("SSE subscription request for job: {}", jobId);
        return sseEmitterService.subscribe(jobId);
    }
}
