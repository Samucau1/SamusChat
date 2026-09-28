package com.chatapp.chatapp_backend.strategy;

import java.util.List;

public interface NotificationStrategy {
    void notify(List<String> recipients, String title,
                String body, String channelId, String senderEmail);
}
