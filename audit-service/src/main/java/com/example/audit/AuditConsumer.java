package com.example.audit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@Service
@Slf4j
public class AuditConsumer {

    @Value("${audit.file.path:audit_log.json}")
    private String auditFilePath;

    @KafkaListener(topics = "api-requests", groupId = "audit-group")
    public void logRequest(String message) {
        writeToAuditLog(message);
    }

    private void writeToAuditLog(String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(auditFilePath, true))) {
            writer.println(message);
            log.debug("Audit log written: {} bytes", message.length());
        } catch (IOException e) {
            log.error("Failed to write audit log: {}", e.getMessage());
        }
    }
}
