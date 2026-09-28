package com.chatapp.chatapp_backend.strategy;

import com.chatapp.chatapp_backend.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("pushStrategy")
@RequiredArgsConstructor
public class PushNotificationStrategy implements NotificationStrategy {

    private final NotificationService notificationService;

    @Override
    public void notify(List<String> recipients, String title,
                       String body, String channelId, String senderEmail) {
        notificationService.sendToUsers(recipients, title, body, channelId, senderEmail);
    }
}
