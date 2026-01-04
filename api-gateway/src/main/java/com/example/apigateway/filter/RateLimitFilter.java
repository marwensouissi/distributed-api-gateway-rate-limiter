package com.example.apigateway.filter;

import com.example.apigateway.model.RequestEvent;
import com.example.apigateway.service.KafkaEventPublisher;
import com.example.apigateway.service.RedisRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RequiredArgsConstructor
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final RedisRateLimiter rateLimiter;
    private final KafkaEventPublisher kafkaPublisher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        String userId = exchange.getAttribute("userId"); // From Auth Filter
        String apiKey = exchange.getAttribute("apiKey"); // From Auth Filter
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod().name();

        return rateLimiter.isAllowed(ip, userId, apiKey, path, method)
                .flatMap(allowed -> {
                    if (allowed) {
                        return chain.filter(exchange);
                    } else {
                        // BLOCKED
                        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);

                        // Publish Blocked Event immediately
                        String requestId = exchange.getAttribute("requestId");
                        if (requestId == null)
                            requestId = UUID.randomUUID().toString();

                        RequestEvent event = RequestEvent.builder()
                                .timestamp(Instant.now().toString())
                                .requestId(requestId)
                                .ip(ip)
                                .userId(userId)
                                .apiKey(apiKey)
                                .endpoint(path)
                                .method(method)
                                .status(429)
                                .latencyMs(0)
                                .type("BLOCKED")
                                .build();

                        kafkaPublisher.publishEvent(event);

                        return exchange.getResponse().setComplete();
                    }
                });
    }

    @Override
    public int getOrder() {
        return -1; // Run before RouteToRequestUrl, after Auth
    }
}
