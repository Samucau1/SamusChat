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
        return Jwts.builder()
                .setSubject(email)
                .claim("av", users == null ? 0 : users.findByEmail(email).map(u -> u.getAuthVersion()).orElse(0))
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
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

    public boolean isTokenValid(String token) {
        try {
            Claims claims=Jwts.parserBuilder().setSigningKey(getKey()).build().parseClaimsJws(token).getBody();
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
