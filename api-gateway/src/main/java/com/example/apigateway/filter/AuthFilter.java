package com.example.apigateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class AuthFilter implements GlobalFilter, Ordered {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String VALID_TOKEN = "valid-token";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_API_KEY = "X-API-KEY";
    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_API_KEY = "apiKey";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        extractAndValidateAuth(exchange);
        return chain.filter(exchange);
    }

    private void extractAndValidateAuth(ServerWebExchange exchange) {
        String authHeader = exchange.getRequest().getHeaders().getFirst(HEADER_AUTHORIZATION);
        String apiKey = exchange.getRequest().getHeaders().getFirst(HEADER_API_KEY);

        if (isValidBearerToken(authHeader)) {
            exchange.getAttributes().put(ATTR_USER_ID, "user-123");
        }

        if (apiKey != null && !apiKey.isEmpty()) {
            exchange.getAttributes().put(ATTR_API_KEY, apiKey);
        }
    }

    private boolean isValidBearerToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return false;
        }
        String token = authHeader.substring(BEARER_PREFIX.length());
        return VALID_TOKEN.equals(token);
    }

    @Override
    public int getOrder() {
        return -2;
    }
}
