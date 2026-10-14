package com.chatapp.chatapp_backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import com.chatapp.chatapp_backend.exception.ApiErrorWriter;
import com.chatapp.chatapp_backend.websocket.WebSocketSessions;
import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.service.PermissionService;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.MessageBuilder;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class SecurityTransportAndDeliveryTests {
    @Test void secureTransportCannotBeClaimedByAnUntrustedHeader() throws Exception {
        var writer=mock(ApiErrorWriter.class);var proxies=mock(RateLimitFilter.class);
        var filter=new TransportSecurityFilter(writer,proxies);ReflectionTestUtils.setField(filter,"required",true);
        var request=new MockHttpServletRequest("POST","/api/auth/login");request.addHeader("X-Forwarded-Proto","https");
        var response=new MockHttpServletResponse();var chain=new MockFilterChain();filter.doFilter(request,response,chain);
        verify(writer).write(response,426,"Use HTTPS");assertThat(chain.getRequest()).isNull();
        request=new MockHttpServletRequest("GET","/api/users/me");request.setSecure(true);chain=new MockFilterChain();filter.doFilter(request,new MockHttpServletResponse(),chain);assertThat(chain.getRequest()).isNotNull();
    }
    @Test void removedMembershipAndRevokedJwtStopOutboundMessages() {
        var jwt=mock(JwtUtil.class);var channels=mock(ChannelRepository.class);var permissions=mock(PermissionService.class);
        var sessions=new WebSocketSessions(jwt,channels,permissions);sessions.connect("session","owner@example.test","jwt");
        var server=new com.chatapp.chatapp_backend.entity.Server();server.setId(9L);
        var target=new com.chatapp.chatapp_backend.entity.Channel();target.setId(7L);target.setServer(server);
        when(jwt.isTokenValid("jwt")).thenReturn(true);when(channels.findById(7L)).thenReturn(Optional.of(target));when(permissions.isMember(9L,"owner@example.test")).thenReturn(true);
        var headers=SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);headers.setSessionId("session");headers.setDestination("/topic/channel/7");
        var message=MessageBuilder.createMessage(new byte[]{1},headers.getMessageHeaders());
        assertThat(sessions.preSend(message,null)).isSameAs(message);
        when(permissions.isMember(9L,"owner@example.test")).thenReturn(false);assertThat(sessions.preSend(message,null)).isNull();
        when(jwt.isTokenValid("jwt")).thenReturn(false);assertThat(sessions.preSend(message,null)).isNull();
    }
}
