package com.example.apigateway.service;

import com.example.apigateway.model.RequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC_REQUESTS = "api-requests";
    private static final String TOPIC_BLOCKED = "api-blocked";
    private static final String TOPIC_ALERTS = "security-alerts";

    public void publishEvent(RequestEvent event) {
        String topic = TOPIC_REQUESTS;
        if ("BLOCKED".equals(event.getType())) {
            topic = TOPIC_BLOCKED;
        } else if ("SECURITY_ALERT".equals(event.getType())) {
            topic = TOPIC_ALERTS;
        }

        // Async fire-and-forget (with logging on error)
        kafkaTemplate.send(topic, event.getRequestId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to pulish event to Kafka: {}", ex.getMessage());
                    }
                });
    }
}
