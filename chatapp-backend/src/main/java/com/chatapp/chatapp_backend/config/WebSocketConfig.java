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

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Prefixo dos tópicos que os clientes vão ESCUTAR
        // Exemplo: cliente se inscreve em /topic/channel/1
        // e recebe todas as mensagens daquele canal
        registry.enableSimpleBroker("/topic");

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
                .setAllowedOriginPatterns("*") // permite qualquer origem
                .withSockJS(); // fallback para ambientes sem WebSocket nativo
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Registra o interceptor que valida o JWT
        // antes de qualquer mensagem ser processada
        registration.interceptors(webSocketAuthInterceptor);
    }
}