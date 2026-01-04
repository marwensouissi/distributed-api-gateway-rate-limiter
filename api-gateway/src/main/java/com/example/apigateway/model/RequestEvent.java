package com.example.apigateway.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RequestEvent {
    private String timestamp;
    private String requestId;
    private String ip;
    private String userId;
    private String apiKey;
    private String endpoint;
    private String method;
    private int status;
    private long latencyMs;
    private String type; // "ALLOWED", "BLOCKED", "SECURITY_ALERT"
}
