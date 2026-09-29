package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisMessageSubscriber implements MessageListener {
    public static final String CHANNEL_PREFIX = "chat:channel:";
    private final SimpMessagingTemplate messaging;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            MessageResponse payload = objectMapper.readValue(message.getBody(), MessageResponse.class);
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            if (payload.getChannelId() == null || !channel.equals(CHANNEL_PREFIX + payload.getChannelId())) {
                log.warn("Ignoring Redis message with mismatched channel");
                return;
            }
            messaging.convertAndSend("/topic/channel/" + payload.getChannelId(), payload);
        } catch (Exception ex) {
            log.warn("Could not process Redis chat message", ex);
        }
    }
}
