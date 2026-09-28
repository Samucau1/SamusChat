package com.chatapp.chatapp_backend.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class MessageBroadcaster {
    private final SimpMessagingTemplate messaging;
    @TransactionalEventListener
    public void onMessageCreated(MessageCreatedEvent event) {
        messaging.convertAndSend("/topic/channel/" + event.message().getChannelId(), event.message());
    }
}
