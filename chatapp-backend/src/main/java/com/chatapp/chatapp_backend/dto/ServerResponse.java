package com.chatapp.chatapp_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ServerResponse {
    private Long id;
    private String name;
    private String description;
    private String ownerEmail;
    private LocalDateTime createdAt;
    private List<ChannelResponse> channels;
}
