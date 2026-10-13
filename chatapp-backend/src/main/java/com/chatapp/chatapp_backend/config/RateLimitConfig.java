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
    private final Map<String, Long> lastUsed = new ConcurrentHashMap<>();
    @org.springframework.beans.factory.annotation.Value("${app.security.rate-limit.general:120}") private long general = 120;
    @org.springframework.beans.factory.annotation.Value("${app.security.rate-limit.auth:60}") private long auth = 60;
    private synchronized Bucket limited(String key, long capacity) {
        long now = System.currentTimeMillis();
        if (buckets.size() >= 10000) { lastUsed.entrySet().removeIf(item -> { if (now - item.getValue() > 120000) { buckets.remove(item.getKey()); return true; } return false; }); }
        if (!buckets.containsKey(key) && buckets.size() >= 10000) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        lastUsed.put(key, now);
        return buckets.computeIfAbsent(key, ignored -> Bucket.builder().addLimit(Bandwidth.classic(capacity, Refill.intervally(capacity, Duration.ofMinutes(1)))).build());
    }

    public Bucket resolveBucket(String ip) {
        return limited(ip, general);
    }

    private Bucket createBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(30, Refill.intervally(30, Duration.ofMinutes(1)));
        return Bucket.builder().addLimit(limit).build();
    }

    public Bucket resolveAuthBucket(String ip) {
        return limited("auth_" + ip, auth);
    }

    public Bucket resolveCallBucket(String ip) {
        return limited("calls_" + ip, 180);
    }
    public Bucket resolveRoomBucket(String ip) {
        return limited("rooms_" + ip, 360);
    }
}
