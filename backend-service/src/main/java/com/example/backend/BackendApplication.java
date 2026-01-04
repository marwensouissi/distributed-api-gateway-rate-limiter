package com.example.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication
@RestController
@RequestMapping("/api/v1")
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    @GetMapping("/resource")
    public String getResource() {
        return "{\"message\": \"Hello from Backend Service\", \"timestamp\": " + System.currentTimeMillis() + "}";
    }

    @GetMapping("/protected")
    public String getProtected() {
        return "{\"message\": \"This is sensitive data\", \"timestamp\": " + System.currentTimeMillis() + "}";
    }
}
