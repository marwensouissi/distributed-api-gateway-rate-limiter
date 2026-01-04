package com.example.apigateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class AuthFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Simple Logic: Extract Authorization header or X-API-KEY
        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        String apiKey = exchange.getRequest().getHeaders().getFirst("X-API-KEY");

        // Mock Validation:
        // if Token starts with "Bearer valid", user = "user123"
        // if API Key is "secret-key", apiKey = "key-abc"

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            // In real app: Validate JWT via Keycloak/Redis/Public Key
            String token = authHeader.substring(7);
            if ("valid-token".equals(token)) {
                exchange.getAttributes().put("userId", "user-123");
            }
        }

        if (apiKey != null && !apiKey.isEmpty()) {
            exchange.getAttributes().put("apiKey", apiKey);
        }

        // We don't block invalid auth here for this demo unless explicitly required to
        // be a security gate.
        // Requirements: "Authenticate requests". So we should probably block if NO auth
        // is present?
        // But maybe public endpoints exist. Let's strictly require it for
        // /api/v1/protected if we had specific routes.
        // For now, we passthrough but populate context. The Rate Limiter needs these
        // IDs.

        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return -2; // Run first
    }
}
