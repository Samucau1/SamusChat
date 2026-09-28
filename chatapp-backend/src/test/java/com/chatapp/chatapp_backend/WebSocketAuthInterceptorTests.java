package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.security.JwtUtil;
import com.chatapp.chatapp_backend.service.PermissionService;
import com.chatapp.chatapp_backend.websocket.WebSocketAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class WebSocketAuthInterceptorTests {
    private final WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(
            mock(JwtUtil.class), mock(ChannelRepository.class), mock(PermissionService.class));

    @Test
    void rejectsConnectWithoutJwt() {
        var headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, null)).isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsAnonymousSubscription() {
        var headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/channel/1");
        var message = MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
        assertThatThrownBy(() -> interceptor.preSend(message, null)).isInstanceOf(SecurityException.class);
    }
}
