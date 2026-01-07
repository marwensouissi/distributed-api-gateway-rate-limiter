package com.example.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}

@RestController
@RequestMapping("/api/v1")
class ApiController {

    @GetMapping(value = "/resource", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getResource() {
        return Map.of(
                "message", "Hello from Backend Service",
                "timestamp", Instant.now().toEpochMilli());
    }

    @GetMapping(value = "/protected", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getProtected() {
        return Map.of(
                "message", "This is sensitive data",
                "timestamp", Instant.now().toEpochMilli());
    }

    @GetMapping(value = "/users", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getUsers() {
        return Map.of(
                "message", "Users endpoint",
                "timestamp", Instant.now().toEpochMilli());
    }

    @GetMapping(value = "/orders", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> getOrders() {
        return Map.of(
                "message", "Orders endpoint",
                "timestamp", Instant.now().toEpochMilli());
    }
}
