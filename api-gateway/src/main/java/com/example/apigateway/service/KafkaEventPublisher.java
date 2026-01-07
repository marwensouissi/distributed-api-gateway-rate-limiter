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

    private static final String TOPIC_REQUESTS = "api-requests";
    private static final String TOPIC_BLOCKED = "api-blocked";
    private static final String TOPIC_ALERTS = "security-alerts";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishEvent(RequestEvent event) {
        String topic = resolveTopic(event.getType());

        kafkaTemplate.send(topic, event.getRequestId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish event to {}: {}", topic, ex.getMessage());
                    } else {
                        log.debug("Event published to {} partition={} offset={}",
                                topic,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    private String resolveTopic(String eventType) {
        return switch (eventType) {
            case "BLOCKED" -> TOPIC_BLOCKED;
            case "SECURITY_ALERT" -> TOPIC_ALERTS;
            default -> TOPIC_REQUESTS;
        };
    }
}
