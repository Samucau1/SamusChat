package com.chatapp.chatapp_backend.config;

import com.chatapp.chatapp_backend.websocket.WebSocketAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    // Interceptor que valida o token JWT na conexão WebSocket
    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final com.chatapp.chatapp_backend.websocket.WebSocketSessions sessions;
    @org.springframework.beans.factory.annotation.Value("${app.websocket.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,http://127.0.0.1:5174,http://127.0.0.1:5175}") private String origins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefixo dos tópicos que os clientes vão ESCUTAR
        // Exemplo: cliente se inscreve em /topic/channel/1
        // e recebe todas as mensagens daquele canal
        registry.enableSimpleBroker("/topic", "/queue");

        // Prefixo das mensagens enviadas pelo cliente AO servidor
        // Exemplo: cliente envia para /app/channel/1
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Endpoint de conexão WebSocket
        // Android conecta em: ws://10.0.2.2:8080/ws
        // Navegador conecta em: ws://localhost:8080/ws
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(origins.split(","))
                .withSockJS(); // fallback para ambientes sem WebSocket nativo
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Registra o interceptor que valida o JWT
        // antes de qualquer mensagem ser processada
        registration.interceptors(webSocketAuthInterceptor);
    }
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) { registration.interceptors(sessions); }
    @Override public void configureWebSocketTransport(org.springframework.web.socket.config.annotation.WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(64 * 1024).setSendBufferSizeLimit(256 * 1024).setSendTimeLimit(10000);
    }
}
