package com.chatapp.chatapp_backend.config;

import com.chatapp.chatapp_backend.strategy.NotificationStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfig {
    @Bean
    public NotificationStrategy notificationStrategy(
            @Value("${app.notifications.strategy:push}") String strategy,
            @Qualifier("pushStrategy") NotificationStrategy push,
            @Qualifier("logStrategy") NotificationStrategy log) {
        return switch (strategy) {
            case "push" -> push;
            case "log" -> log;
            default -> throw new IllegalArgumentException("app.notifications.strategy deve ser push ou log");
        };
    }
}
