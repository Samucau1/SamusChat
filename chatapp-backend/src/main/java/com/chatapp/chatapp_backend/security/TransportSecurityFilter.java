package com.chatapp.chatapp_backend.security;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import com.chatapp.chatapp_backend.exception.ApiErrorWriter;

@Component @RequiredArgsConstructor
public class TransportSecurityFilter extends OncePerRequestFilter {
    @Value("${app.security.require-https:false}") private boolean required;
    private final ApiErrorWriter writer;
    private final RateLimitFilter proxies;
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        boolean secure = request.isSecure() || proxies.isTrustedProxy(request) && "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        if (required && !secure && !request.getRequestURI().startsWith("/actuator/health")) {
            writer.write(response, 426, "Use HTTPS"); return;
        }
        if (required && secure) response.setHeader("Strict-Transport-Security", "max-age=31536000");
        chain.doFilter(request, response);
    }
}
