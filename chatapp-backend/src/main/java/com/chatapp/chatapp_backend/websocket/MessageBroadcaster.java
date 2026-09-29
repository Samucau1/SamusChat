package com.chatapp.chatapp_backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class MessageBroadcaster {
    private final SimpMessagingTemplate messaging;
    private final ObjectProvider<StringRedisTemplate> redis;
    private final ObjectMapper objectMapper;

    // AFTER_COMMIT: REST, STOMP and attachments share the same publication path.
    @TransactionalEventListener
    public void onMessageCreated(MessageCreatedEvent event) {
        StringRedisTemplate publisher = redis.getIfAvailable();
        if (publisher != null) {
            try {
                publisher.convertAndSend(RedisMessageSubscriber.CHANNEL_PREFIX + event.message().getChannelId(),
                        objectMapper.writeValueAsString(event.message()));
                // The Redis subscriber delivers locally too; do not broadcast twice.
                return;
            } catch (JsonProcessingException | org.springframework.dao.DataAccessException ex) {
                log.warn("Redis publication failed for message {}; delivering locally", event.message().getId(), ex);
            }
        }
        messaging.convertAndSend("/topic/channel/" + event.message().getChannelId(), event.message());
    }
}
