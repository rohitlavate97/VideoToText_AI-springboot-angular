package com.videototext.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "videototext.exchange";
    public static final String DLX_EXCHANGE_NAME = "videototext.dlx";

    public static final String TRANSCRIPTION_REQUEST_QUEUE = "transcription.requests";
    public static final String TRANSCRIPTION_RESULT_QUEUE = "transcription.results";
    public static final String PROCESSING_EVENT_QUEUE = "processing.events";
    public static final String DLQ_QUEUE = "transcription.dlq";

    public static final String TRANSCRIPTION_REQUEST_ROUTING_KEY = "transcription.request";
    public static final String TRANSCRIPTION_RESULT_ROUTING_KEY = "transcription.result";
    public static final String PROCESSING_EVENT_ROUTING_KEY = "processing.event";
    public static final String DLQ_ROUTING_KEY = "transcription.dead";

    // =========================================================================
    // Exchanges
    // =========================================================================
    @Bean
    public TopicExchange mainExchange() {
        return new TopicExchange(EXCHANGE_NAME, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DLX_EXCHANGE_NAME, true, false);
    }

    // =========================================================================
    // Queues
    // =========================================================================
    @Bean
    public Queue transcriptionRequestQueue() {
        return QueueBuilder.durable(TRANSCRIPTION_REQUEST_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE_NAME)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue transcriptionResultQueue() {
        return QueueBuilder.durable(TRANSCRIPTION_RESULT_QUEUE).build();
    }

    @Bean
    public Queue processingEventQueue() {
        return QueueBuilder.durable(PROCESSING_EVENT_QUEUE).build();
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    // =========================================================================
    // Bindings
    // =========================================================================
    @Bean
    public Binding transcriptionRequestBinding(Queue transcriptionRequestQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(transcriptionRequestQueue)
                .to(mainExchange)
                .with(TRANSCRIPTION_REQUEST_ROUTING_KEY);
    }

    @Bean
    public Binding transcriptionResultBinding(Queue transcriptionResultQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(transcriptionResultQueue)
                .to(mainExchange)
                .with(TRANSCRIPTION_RESULT_ROUTING_KEY);
    }

    @Bean
    public Binding processingEventBinding(Queue processingEventQueue, TopicExchange mainExchange) {
        return BindingBuilder.bind(processingEventQueue)
                .to(mainExchange)
                .with(PROCESSING_EVENT_ROUTING_KEY);
    }

    @Bean
    public Binding deadLetterBinding(Queue deadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(deadLetterQueue)
                .to(deadLetterExchange)
                .with(DLQ_ROUTING_KEY);
    }

    // =========================================================================
    // Serialization / Deserialization
    // =========================================================================
    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jsonMessageConverter);
        return factory;
    }
}
