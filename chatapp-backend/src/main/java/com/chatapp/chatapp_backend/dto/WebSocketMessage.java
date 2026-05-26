package com.chatapp.chatapp_backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

// Objeto trafegado pelo WebSocket em tempo real
@Data
public class WebSocketMessage {
    private Long id;
    private String content;
    private String senderEmail;
    private String senderUsername;
    private Long channelId;
    private String type; // CHAT, JOIN, LEAVE
    private LocalDateTime createdAt;
}