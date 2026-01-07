package com.example.apigateway.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service("customRedisRateLimiter")
@Slf4j
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final int LIMIT_IP = 100;
    private static final int LIMIT_USER = 500;
    private static final int LIMIT_API_KEY = 1000;
    private static final int LIMIT_ENDPOINT = 1000;
    private static final int LIMIT_METHOD = 2000;
    private static final int WINDOW_MS = 60000;

    private final ReactiveStringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript = RedisScript.of(
            new ClassPathResource("scripts/sliding_window.lua"), Long.class);

    public Mono<Boolean> isAllowed(String ip, String userId, String apiKey, String path, String method) {
        long now = Instant.now().toEpochMilli();
        List<String> keys = buildKeys(ip, userId, apiKey, path, method);
        List<String> args = buildArgs(now, userId, apiKey);

        return redisTemplate.execute(rateLimitScript, keys, args)
                .next()
                .map(result -> result == 0)
                .onErrorResume(this::handleRedisError);
    }

    private List<String> buildKeys(String ip, String userId, String apiKey, String path, String method) {
        List<String> keys = new ArrayList<>();
        keys.add("ratelimit:ip:" + ip);

        if (userId != null) {
            keys.add("ratelimit:user:" + userId);
        }
        if (apiKey != null) {
            keys.add("ratelimit:apikey:" + apiKey);
        }

        keys.add("ratelimit:path:" + path);
        keys.add("ratelimit:method:" + method);
        return keys;
    }

    private List<String> buildArgs(long now, String userId, String apiKey) {
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(WINDOW_MS));
        args.add(String.valueOf(now));
        args.add(String.valueOf(LIMIT_IP));

        if (userId != null) {
            args.add(String.valueOf(LIMIT_USER));
        }
        if (apiKey != null) {
            args.add(String.valueOf(LIMIT_API_KEY));
        }

        args.add(String.valueOf(LIMIT_ENDPOINT));
        args.add(String.valueOf(LIMIT_METHOD));
        return args;
    }

    private Mono<Boolean> handleRedisError(Throwable e) {
        log.error("Redis rate limiter error: {}", e.getMessage());
        return Mono.just(true);
    }
}
