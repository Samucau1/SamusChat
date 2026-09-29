package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RedisMessagingTests {
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
    private final MessageBroadcaster broadcaster = new MessageBroadcaster(messaging, provider, mapper);
    private MessageResponse payload;

    @BeforeEach
    void setup() {
        payload = MessageResponse.builder().id(42L).channelId(7L).content("Olá, ação! 🚀")
                .senderEmail("user@test.com").senderUsername("user")
                .attachmentUrl("https://chat.example/uploads/test.png").attachmentType("IMAGE")
                .createdAt(LocalDateTime.of(2026, 9, 29, 12, 0)).build();
    }

    @Test
    void publishesPlainJsonWithoutAlsoBroadcastingLocally() throws Exception {
        when(provider.getIfAvailable()).thenReturn(redis);
        broadcaster.onMessageCreated(new MessageCreatedEvent(payload));
        verify(redis).convertAndSend("chat:channel:7", mapper.writeValueAsString(payload));
        verifyNoInteractions(messaging);
    }

    @Test
    void disabledRedisKeepsLocalDelivery() {
        broadcaster.onMessageCreated(new MessageCreatedEvent(payload));
        verify(messaging).convertAndSend("/topic/channel/7", payload);
        verifyNoInteractions(redis);
    }

    @Test
    void redisFailureFallsBackLocally() {
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.convertAndSend(anyString(), anyString())).thenThrow(new RedisConnectionFailureException("offline"));
        broadcaster.onMessageCreated(new MessageCreatedEvent(payload));
        verify(messaging).convertAndSend("/topic/channel/7", payload);
    }

    @Test
    void subscriberPreservesUnicodeAttachmentsAndTimestamp() throws Exception {
        new RedisMessageSubscriber(messaging, mapper).onMessage(
                new DefaultMessage("chat:channel:7".getBytes(StandardCharsets.UTF_8), mapper.writeValueAsBytes(payload)), null);
        verify(messaging).convertAndSend("/topic/channel/7", payload);
    }

    @Test
    void malformedOrMismatchedMessagesAreIgnored() throws Exception {
        var subscriber = new RedisMessageSubscriber(messaging, mapper);
        subscriber.onMessage(new DefaultMessage("chat:channel:7".getBytes(StandardCharsets.UTF_8), new byte[]{0}), null);
        subscriber.onMessage(new DefaultMessage("chat:channel:9".getBytes(StandardCharsets.UTF_8), mapper.writeValueAsBytes(payload)), null);
        payload.setChannelId(null);
        subscriber.onMessage(new DefaultMessage("chat:channel:7".getBytes(StandardCharsets.UTF_8), mapper.writeValueAsBytes(payload)), null);
        verifyNoInteractions(messaging);
    }
}
