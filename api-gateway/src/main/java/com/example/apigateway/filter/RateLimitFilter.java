package com.example.apigateway.filter;

import com.example.apigateway.model.RequestEvent;
import com.example.apigateway.service.KafkaEventPublisher;
import com.example.apigateway.service.RedisRateLimiter;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private static final String ATTR_REQUEST_ID = "requestId";
    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_API_KEY = "apiKey";

    private final RedisRateLimiter rateLimiter;
    private final KafkaEventPublisher kafkaPublisher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = extractClientIp(exchange);
        String userId = exchange.getAttribute(ATTR_USER_ID);
        String apiKey = exchange.getAttribute(ATTR_API_KEY);
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        return rateLimiter.isAllowed(ip, userId, apiKey, path, method)
                .flatMap(allowed -> allowed
                        ? chain.filter(exchange)
                        : handleRateLimitExceeded(exchange, ip, userId, apiKey, path, method));
    }

    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange, String ip,
            String userId, String apiKey, String path, String method) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        publishBlockedEvent(exchange, ip, userId, apiKey, path, method);
        return exchange.getResponse().setComplete();
    }

    private void publishBlockedEvent(ServerWebExchange exchange, String ip,
            String userId, String apiKey, String path, String method) {
        String requestId = exchange.getAttribute(ATTR_REQUEST_ID);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        RequestEvent event = RequestEvent.builder()
                .timestamp(Instant.now().toString())
                .requestId(requestId)
                .ip(ip)
                .userId(userId)
                .apiKey(apiKey)
                .endpoint(path)
                .method(method)
                .status(HttpStatus.TOO_MANY_REQUESTS.value())
                .latencyMs(0)
                .type("BLOCKED")
                .build();

        kafkaPublisher.publishEvent(event);
    }

    private String extractClientIp(ServerWebExchange exchange) {
        return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
