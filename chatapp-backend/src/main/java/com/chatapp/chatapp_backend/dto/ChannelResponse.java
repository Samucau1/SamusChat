package com.chatapp.chatapp_backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ChannelResponse {
    private Long id;
    private String name;
    private String type;
    private LocalDateTime createdAt;
}