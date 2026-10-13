package com.chatapp.chatapp_backend.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AccountLoginLimiter {
    private final ObjectProvider<StringRedisTemplate> redis;
    private record Attempts(int count, long expires) {}
    private final Map<String, Attempts> local = new ConcurrentHashMap<>();
    private static final DefaultRedisScript<Long> INCREMENT = new DefaultRedisScript<>(
        "local n=redis.call('INCR',KEYS[1]); if n==1 then redis.call('EXPIRE',KEYS[1],60) end; return n", Long.class);
    public AccountLoginLimiter(ObjectProvider<StringRedisTemplate> redis) { this.redis = redis; }
    private String key(String email) {
        try { return "security:login:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(email.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public void attempt(String email) {
        String key = key(email); var client = redis.getIfAvailable(); long count;
        if (client != null) {
            try { count = Objects.requireNonNull(client.execute(INCREMENT, List.of(key))); }
            catch (org.springframework.data.redis.RedisConnectionFailureException failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Protecao de login indisponivel"); }
        } else {
            long now = System.currentTimeMillis();
            local.entrySet().removeIf(item -> item.getValue().expires <= now);
            if (!local.containsKey(key) && local.size() >= 10000) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS);
            count = local.compute(key, (k, old) -> old == null || old.expires <= now ? new Attempts(1, now + 60000) : new Attempts(old.count + 1, old.expires)).count;
        }
        if (count > 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde antes de tentar novamente");
    }
    public void success(String email) { var client = redis.getIfAvailable(); if (client != null) client.delete(key(email)); else local.remove(key(email)); }
}
