package com.chatapp.chatapp_backend.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MessageResponse {
    private Long id;
    private String content;
    private String senderEmail;
    private String senderUsername;
    private Long channelId;
    private String attachmentUrl;
    private String attachmentType;
    private LocalDateTime createdAt;
}
