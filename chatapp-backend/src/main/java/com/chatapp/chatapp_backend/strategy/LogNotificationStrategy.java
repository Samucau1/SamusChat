package com.chatapp.chatapp_backend.strategy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("logStrategy")
public class LogNotificationStrategy implements NotificationStrategy {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationStrategy.class);

    @Override
    public void notify(List<String> recipients, String title,
                       String body, String channelId, String senderEmail) {
        log.info("[NOTIFICACAO] Para: {} | Titulo: {} | Corpo: {} | Canal: {}",
                recipients, title, body, channelId);
    }
}
