package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.entity.DirectMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class DirectMessageBroadcaster {
    public static final String REDIS_CHANNEL = "chat:direct";
    public static final String QUEUE = "/queue/direct-messages";
    private final SimpMessagingTemplate messaging;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final ObjectMapper mapper;

    @TransactionalEventListener
    public void created(DirectMessageCreatedEvent event) {
        var publisher = redis.getIfAvailable();
        if (publisher != null) {
            try {
                publisher.convertAndSend(REDIS_CHANNEL, mapper.writeValueAsString(event.message()));
                return;
            } catch (JsonProcessingException | org.springframework.dao.DataAccessException failure) {
                log.warn("Direct message {}: Redis unavailable; local delivery", event.message().getId());
            }
        }
        deliver(event.message());
    }

    public void deliver(DirectMessage message) {
        messaging.convertAndSendToUser(message.getSenderEmail(), QUEUE, message);
        messaging.convertAndSendToUser(message.getRecipientEmail(), QUEUE, message);
    }
}
