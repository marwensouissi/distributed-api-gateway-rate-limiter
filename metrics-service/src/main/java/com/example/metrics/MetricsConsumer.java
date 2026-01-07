package com.example.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MetricsConsumer {

    private static final String STATS_KEY_PREFIX = "stats:endpoint:";
    private static final String FIELD_COUNT = "count";
    private static final String FIELD_ERRORS = "errors";
    private static final String FIELD_TOTAL_LATENCY = "totalLatencyMs";
    private static final int ERROR_STATUS_THRESHOLD = 400;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "api-requests", groupId = "metrics-group")
    public void process(String message) {
        try {
            JsonNode node = mapper.readTree(message);
            String endpoint = node.path("endpoint").asText();
            int status = node.path("status").asInt();
            long latencyMs = node.path("latencyMs").asLong();

            String key = STATS_KEY_PREFIX + endpoint;
            incrementStats(key, status, latencyMs);

            log.debug("Processed metrics for endpoint: {}", endpoint);
        } catch (Exception e) {
            log.error("Failed to process metrics message: {}", e.getMessage());
        }
    }

    private void incrementStats(String key, int status, long latencyMs) {
        redisTemplate.opsForHash().increment(key, FIELD_COUNT, 1);
        redisTemplate.opsForHash().increment(key, FIELD_TOTAL_LATENCY, latencyMs);

        if (status >= ERROR_STATUS_THRESHOLD) {
            redisTemplate.opsForHash().increment(key, FIELD_ERRORS, 1);
        }
    }
}
