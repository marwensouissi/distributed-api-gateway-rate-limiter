package com.example.analytics;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
public class AnalyticsApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnalyticsApplication.class, args);
    }
}

@Service
@Slf4j
class RequestConsumer {

    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "api-requests", groupId = "analytics-group")
    public void consumeRequest(ConsumerRecord<String, String> record) {
        try {
            // Logic: Parse JSON, Update Redis counters, Detect Anomalies
            String value = record.value();
            // log.info("Received Request Event: {}", value);
            // In real world: Use metricsService.record(event)
        } catch (Exception e) {
            log.error("Error processing event", e);
        }
    }

    @KafkaListener(topics = "api-blocked", groupId = "security-group")
    public void consumeBlocked(String msg) {
        log.warn("SECURITY ALERT: Blocked Request detected: {}", msg);
        // Logic: Emit to security-alerts topic if threshold exceeded
    }
}
