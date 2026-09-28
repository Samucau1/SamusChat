package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
    private final com.chatapp.chatapp_backend.repository.ChannelRepository channelRepository;
    private final com.chatapp.chatapp_backend.service.PermissionService permissionService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
            MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        // Só valida na conexão inicial
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                if (jwtUtil.isTokenValid(token)) {
                    String email = jwtUtil.extractEmail(token);

                    // Registra o usuário autenticado na sessão WebSocket
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(email, null, List.of());

                    accessor.setUser(auth);
                }
            }
        }

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand()) && accessor.getUser() == null) {
            throw new SecurityException("Nao autorizado");
        }
        if (accessor != null && (StompCommand.SUBSCRIBE.equals(accessor.getCommand()) || StompCommand.SEND.equals(accessor.getCommand()))) {
            if (accessor.getUser() == null) throw new SecurityException("Nao autorizado");
            String destination = accessor.getDestination();
            String prefix = StompCommand.SUBSCRIBE.equals(accessor.getCommand()) ? "/topic/channel/" : "/app/channel/";
            if (destination == null || !destination.startsWith(prefix)) throw new SecurityException("Destino invalido");
            Long channelId = Long.valueOf(destination.substring(prefix.length()));
            var target = channelRepository.findById(channelId).orElseThrow(() -> new SecurityException("Canal invalido"));
            permissionService.requireMember(target.getServer().getId(), accessor.getUser().getName());
        }
        return message;
    }
}
