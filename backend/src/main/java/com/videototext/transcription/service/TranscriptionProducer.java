package com.videototext.transcription.service;

import com.videototext.config.RabbitMQConfig;
import com.videototext.transcription.dto.TranscriptionMessagePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptionProducer {

    private final RabbitTemplate rabbitTemplate;

    public void publishJobRequest(TranscriptionMessagePayload payload) {
        log.info("Publishing transcription request for job: {} [engine={}, model={}, video={}]",
                payload.getJobId(), payload.getEngine(), payload.getModel(), payload.getVideoId());

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.TRANSCRIPTION_REQUEST_ROUTING_KEY,
                payload
        );
    }
}
