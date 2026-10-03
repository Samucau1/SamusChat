package com.chatapp.chatapp_backend.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String ip) {
        return buckets.computeIfAbsent(ip, this::createBucket);
    }

    private Bucket createBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(30, Refill.intervally(30, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    public Bucket resolveAuthBucket(String ip) {
        return buckets.computeIfAbsent("auth_" + ip, key -> {
            Bandwidth limit = Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)));
            return Bucket.builder().addLimit(limit).build();
        });
    }

    public Bucket resolveCallBucket(String ip) {
        return buckets.computeIfAbsent("calls_" + ip, key -> Bucket.builder()
            .addLimit(Bandwidth.classic(180, Refill.intervally(180, Duration.ofMinutes(1)))).build());
    }
    public Bucket resolveRoomBucket(String ip) {
        return buckets.computeIfAbsent("rooms_" + ip, key -> Bucket.builder()
            .addLimit(Bandwidth.classic(360, Refill.intervally(360, Duration.ofMinutes(1)))).build());
    }
}
