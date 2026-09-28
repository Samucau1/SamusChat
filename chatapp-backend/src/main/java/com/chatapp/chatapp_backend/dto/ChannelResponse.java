package com.chatapp.chatapp_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ChannelResponse {
    private Long id;
    private String name;
    private String type;
    private LocalDateTime createdAt;
}
