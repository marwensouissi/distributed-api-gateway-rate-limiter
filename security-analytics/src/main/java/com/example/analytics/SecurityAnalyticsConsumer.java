package com.example.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class SecurityAnalyticsConsumer {

    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "api-requests", groupId = "analytics-group")
    public void processRequest(ConsumerRecord<String, String> record) {
        try {
            JsonNode node = mapper.readTree(record.value());
            analyzeRequest(node);
        } catch (Exception e) {
            log.error("Failed to process request event: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "api-blocked", groupId = "security-group")
    public void processBlockedRequest(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            handleBlockedRequest(node);
        } catch (Exception e) {
            log.error("Failed to process blocked event: {}", e.getMessage());
        }
    }

    private void analyzeRequest(JsonNode node) {
        String ip = node.path("ip").asText();
        String endpoint = node.path("endpoint").asText();
        int status = node.path("status").asInt();

        log.debug("Analyzing request: ip={}, endpoint={}, status={}", ip, endpoint, status);
    }

    private void handleBlockedRequest(JsonNode node) {
        String ip = node.path("ip").asText();
        String endpoint = node.path("endpoint").asText();
        String requestId = node.path("requestId").asText();

        log.warn("Blocked request: requestId={}, ip={}, endpoint={}", requestId, ip, endpoint);
    }
}
