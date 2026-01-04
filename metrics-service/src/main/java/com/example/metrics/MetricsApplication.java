package com.example.metrics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
public class MetricsApplication {
    public static void main(String[] args) {
        SpringApplication.run(MetricsApplication.class, args);
    }
}

@Service
@Slf4j
class MetricsConsumer {
    // We aggregate metrics into Redis:
    // HINCRBY stats:endpoints:{endpoint} "count" 1
    // HINCRBY stats:endpoints:{endpoint} "errors" 1 (if status >= 400)

    @Autowired
    private StringRedisTemplate redisTemplate;

    private final ObjectMapper mapper = new ObjectMapper();

    @KafkaListener(topics = "api-requests", groupId = "metrics-group")
    public void process(String msg) {
        try {
            JsonNode node = mapper.readTree(msg);
            String endpoint = node.path("endpoint").asText();
            int status = node.path("status").asInt();

            String key = "stats:endpoint:" + endpoint;
            redisTemplate.opsForHash().increment(key, "count", 1);
            if (status >= 400) {
                redisTemplate.opsForHash().increment(key, "errors", 1);
            }

            // P95 Latency - In a real app we would use T-Digest or HDRHistogram or push to
            // Prometheus/TimescaleDB
            // Here we just log for demonstration
            // long latency = node.path("latencyMs").asLong();
        } catch (Exception e) {
            log.error("Metrics error", e);
        }
    }
}
