package com.example.apigateway.filter;

import com.example.apigateway.model.RequestEvent;
import com.example.apigateway.service.KafkaEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ObservabilityFilter implements GlobalFilter, Ordered {

    private static final String ATTR_REQUEST_ID = "requestId";
    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_API_KEY = "apiKey";
    private static final int STATUS_RATE_LIMITED = 429;
    private static final int DEFAULT_ERROR_STATUS = 500;

    private final KafkaEventPublisher kafkaPublisher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        exchange.getAttributes().put(ATTR_REQUEST_ID, requestId);

        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> publishEvent(exchange, requestId, startTime)));
    }

    private void publishEvent(ServerWebExchange exchange, String requestId, long startTime) {
        long latency = System.currentTimeMillis() - startTime;
        ServerHttpRequest request = exchange.getRequest();
        int status = getResponseStatus(exchange);

        RequestEvent event = RequestEvent.builder()
                .timestamp(Instant.now().toString())
                .requestId(requestId)
                .ip(extractClientIp(request))
                .userId(exchange.getAttribute(ATTR_USER_ID))
                .apiKey(exchange.getAttribute(ATTR_API_KEY))
                .endpoint(request.getPath().value())
                .method(request.getMethod().name())
                .status(status)
                .latencyMs(latency)
                .type(status == STATUS_RATE_LIMITED ? "BLOCKED" : "ALLOWED")
                .build();

        kafkaPublisher.publishEvent(event);
    }

    private int getResponseStatus(ServerWebExchange exchange) {
        return exchange.getResponse().getStatusCode() != null
                ? exchange.getResponse().getStatusCode().value()
                : DEFAULT_ERROR_STATUS;
    }

    private String extractClientIp(ServerHttpRequest request) {
        return request.getRemoteAddress() != null
                ? request.getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
