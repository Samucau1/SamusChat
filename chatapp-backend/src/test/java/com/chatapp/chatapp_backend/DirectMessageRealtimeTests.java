package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.entity.DirectMessage;
import com.chatapp.chatapp_backend.websocket.*;
import com.chatapp.chatapp_backend.security.JwtUtil;
import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.service.PermissionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class DirectMessageRealtimeTests {
    @Test void localDeliveryTargetsBothParticipantsOnly() {
        var messaging = mock(SimpMessagingTemplate.class);
        @SuppressWarnings("unchecked") ObjectProvider<StringRedisTemplate> redis = mock(ObjectProvider.class);
        var broadcaster = new DirectMessageBroadcaster(messaging, redis, new ObjectMapper());
        var message = new DirectMessage(); message.setSenderEmail("a@test.com"); message.setRecipientEmail("b@test.com");
        broadcaster.created(new DirectMessageCreatedEvent(message));
        verify(messaging).convertAndSendToUser("a@test.com", "/queue/direct-messages", message);
        verify(messaging).convertAndSendToUser("b@test.com", "/queue/direct-messages", message);
        verifyNoMoreInteractions(messaging);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/user/b@test.com/queue/direct-messages", "/queue/direct-messages-userother", "/topic/direct/*", "/user/queue/*"})
    void rejectsForeignAndWildcardSubscriptions(String destination) {
        var sessions = mock(WebSocketSessions.class); when(sessions.valid("s")).thenReturn(true);
        var interceptor = new WebSocketAuthInterceptor(mock(JwtUtil.class), mock(ChannelRepository.class), mock(PermissionService.class), sessions);
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSessionId("s"); headers.setUser(new UsernamePasswordAuthenticationToken("a@test.com", null, List.of()));
        headers.setDestination(destination);
        var frame = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(frame, null)).isInstanceOf(SecurityException.class);
    }

    @Test void privateDeliveryRequiresOwnSessionAndValidJwt() {
        var jwt = mock(JwtUtil.class); when(jwt.isTokenValid("token")).thenReturn(true);
        var sessions = new WebSocketSessions(jwt, mock(ChannelRepository.class), mock(PermissionService.class));
        sessions.connect("s", "a@test.com", "token");
        var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE); headers.setSessionId("s");
        headers.setDestination("/queue/direct-messages-users");
        var frame = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThat(sessions.preSend(frame, null)).isSameAs(frame);
        when(jwt.isTokenValid("token")).thenReturn(false);
        assertThat(sessions.preSend(frame, null)).isNull();
        when(jwt.isTokenValid("token")).thenReturn(true);
        headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE); headers.setSessionId("s");
        headers.setDestination("/queue/direct-messages-userother");
        assertThat(sessions.preSend(MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders()), null)).isNull();
    }
}
