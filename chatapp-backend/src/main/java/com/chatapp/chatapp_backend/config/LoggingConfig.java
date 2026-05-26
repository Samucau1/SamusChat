package com.chatapp.chatapp_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class LoggingConfig extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(LoggingConfig.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        long start = Instant.now().toEpochMilli();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null) {
            ip = request.getRemoteAddr();
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = Instant.now().toEpochMilli() - start;

            log.info("[{}] {} {} -> {} ({}ms)",
                    ip,
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration
            );

            if (duration > 2000) {
                log.warn("Requisicao lenta detectada: {} {}ms", request.getRequestURI(), duration);
            }

            if (response.getStatus() >= 400) {
                log.warn("Erro HTTP {} em {} [IP: {}]",
                        response.getStatus(), request.getRequestURI(), ip);
            }
        }
    }
}
