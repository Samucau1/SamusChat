package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.dto.MessageRequest;
import com.chatapp.chatapp_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {
    private final MessageService messageService;

    @MessageMapping("/channel/{channelId}")
    public void sendMessage(@DestinationVariable Long channelId, @Payload MessageRequest request, Principal principal) {
        if (principal == null) throw new SecurityException("Nao autorizado");
        messageService.sendMessage(principal.getName(), channelId, request);
    }
}
