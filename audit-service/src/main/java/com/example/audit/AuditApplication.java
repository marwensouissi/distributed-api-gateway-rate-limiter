package com.example.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.IOException;

@SpringBootApplication
public class AuditApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditApplication.class, args);
    }
}

@Service
@Slf4j
class AuditConsumer {

    // Simple File Appender to simulate immutable log storage
    // In production: Write to S3, Elasticsearch, or database

    private static final String AUDIT_FILE = "audit_log.json";

    @KafkaListener(topics = "api-requests", groupId = "audit-group")
    public void logRequest(String msg) {
        try (PrintWriter out = new PrintWriter(new FileWriter(AUDIT_FILE, true))) {
            out.println(msg);
        } catch (IOException e) {
            log.error("Failed to write audit log", e);
        }
    }
}
