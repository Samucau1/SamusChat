package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.dto.MessageRequest;
import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.dto.WebSocketMessage;
import com.chatapp.chatapp_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;

    // Cliente envia para: /app/channel/{channelId}
    // Servidor distribui para: /topic/channel/{channelId}
    @MessageMapping("/channel/{channelId}")
    public void sendMessage(
            @DestinationVariable Long channelId,
            @Payload MessageRequest request,
            Principal principal) {

        // principal.getName() retorna o email do JWT
        String email = principal.getName();

        // Salva no banco via MessageService (já existe)
        MessageResponse saved = messageService.sendMessage(email, channelId, request);

        // Monta o objeto WebSocket
        WebSocketMessage wsMessage = new WebSocketMessage();
        wsMessage.setId(saved.getId());
        wsMessage.setContent(saved.getContent());
        wsMessage.setSenderEmail(saved.getSenderEmail());
        wsMessage.setSenderUsername(saved.getSenderUsername());
        wsMessage.setChannelId(channelId);
        wsMessage.setType("CHAT");
        wsMessage.setCreatedAt(saved.getCreatedAt());

        // Distribui para todos os clientes conectados no canal
        messagingTemplate.convertAndSend(
            "/topic/channel/" + channelId,
            wsMessage
        );
    }
}