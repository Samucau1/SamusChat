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
    public void connect(String id, String email, String token) { if (id != null) sessions.put(id, new Session(email, token)); }
    @EventListener public void disconnect(SessionDisconnectEvent event) { sessions.remove(event.getSessionId()); }
    public boolean valid(String id) { var session = sessions.get(id); return session != null && jwt.isTokenValid(session.token); }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) return message;
        String id = SimpMessageHeaderAccessor.getSessionId(message.getHeaders());
        var session = id == null ? null : sessions.get(id);
        String destination = SimpMessageHeaderAccessor.getDestination(message.getHeaders());
        if (session == null || !jwt.isTokenValid(session.token) || destination == null || !destination.matches("/topic/channel/[0-9]+")) return null;
        var target = channels.findById(Long.valueOf(destination.substring("/topic/channel/".length())));
        return target.isPresent() && permissions.isMember(target.get().getServer().getId(), session.email) ? message : null;
    }
}
