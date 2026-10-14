package com.chatapp.chatapp_backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtil {
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    private com.chatapp.chatapp_backend.repository.UserRepository users;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(String email) {
        return generate(email, expiration, "access");
    }

    public String generateRefreshToken(String email) {
        return generate(email, 30L * 24 * 60 * 60 * 1000, "refresh");
    }

    private String generate(String email, long lifetime, String kind) {
        return Jwts.builder()
                .setSubject(email)
                .claim("kind", kind)
                .claim("av", users == null ? 0 : users.findByEmail(email).map(u -> u.getAuthVersion()).orElse(0))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + lifetime))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
    }

    public boolean isTokenValid(String token) { return valid(token, false); }

    public String refreshAccessToken(String token) {
        if (!valid(token, true)) throw new SecurityException("Sessao expirada ou revogada");
        return generateToken(extractEmail(token));
    }

    private boolean valid(String token, boolean refresh) {
        try {
            Claims claims=Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token).getBody();
            String kind = claims.get("kind", String.class);
            if (refresh ? !"refresh".equals(kind) : kind != null && !"access".equals(kind)) return false;
            if(users==null) return true;
            Number version=claims.get("av",Number.class);
            int actual=version==null ? 0 : version.intValue();
            return users.findByEmail(claims.getSubject()).map(u -> u.getAuthVersion()==actual).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private Key getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
