package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.security.JwtUtil;
import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.*;
import org.springframework.messaging.support.ChannelInterceptor;
import java.util.concurrent.ConcurrentHashMap;

@Component @RequiredArgsConstructor
public class WebSocketSessions implements ChannelInterceptor {
    private record Session(String email, String token) {}
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final JwtUtil jwt;
    private final ChannelRepository channels;
    private final PermissionService permissions;
    @org.springframework.beans.factory.annotation.Autowired
    private com.chatapp.chatapp_backend.security.PublicIdentityService identities;
    private Message<?> external(Message<?> message) {
        if (identities == null || !(message.getPayload() instanceof byte[] bytes)) return message;
        return org.springframework.messaging.support.MessageBuilder.createMessage(identities.externalFrame(bytes), message.getHeaders());
    }
    public void connect(String id, String email, String token) { if (id != null) sessions.put(id, new Session(email, token)); }
    @EventListener public void disconnect(SessionDisconnectEvent event) { sessions.remove(event.getSessionId()); }
    public boolean valid(String id) { var session = sessions.get(id); return session != null && jwt.isTokenValid(session.token); }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        String id = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        var session = id == null ? null : sessions.get(id);
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (session == null || !jwt.isTokenValid(session.token) || destination == null) return null;
        if (destination.equals("/queue/direct-messages-user" + id)) return external(message);
        if (!destination.matches("/topic/channel/[0-9]+")) return null;
        var target = channels.findById(Long.valueOf(destination.substring("/topic/channel/".length())));
        return target.isPresent() && permissions.isMember(target.get().getServer().getId(), session.email) ? external(message) : null;
    }
}
