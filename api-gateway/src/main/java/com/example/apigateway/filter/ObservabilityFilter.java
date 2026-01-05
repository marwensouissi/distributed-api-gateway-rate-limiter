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

    private final KafkaEventPublisher kafkaPublisher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        String requestId = UUID.randomUUID().toString();
        exchange.getAttributes().put("requestId", requestId);

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            ServerHttpRequest request = exchange.getRequest();

            // Extract Attributes set by Auth Filter
            String userId = exchange.getAttribute("userId");
            String apiKey = exchange.getAttribute("apiKey");

            // Determine the status and event type
            int status = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value()
                    : 500;
            String eventType = (status == 429) ? "BLOCKED" : "ALLOWED";

            RequestEvent event = RequestEvent.builder()
                    .timestamp(Instant.now().toString())
                    .requestId(requestId)
                    .ip(request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress()
                            : "unknown")
                    .userId(userId)
                    .apiKey(apiKey)
                    .endpoint(request.getPath().value())
                    .method(request.getMethod().name())
                    .status(status)
                    .latencyMs(duration)
                    .type(eventType)
                    .build();

            kafkaPublisher.publishEvent(event);
        }));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE; // Post-filter needs to run last in response phase
    }
}
