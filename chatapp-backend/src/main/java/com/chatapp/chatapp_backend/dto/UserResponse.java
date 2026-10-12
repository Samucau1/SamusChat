package com.chatapp.chatapp_backend.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {
    private Long id;
    private String avatarUrl;
    private String bannerUrl;
    private String backgroundUrl;
    private String accentColor;
    private String bio;
    private String username;
    private String email;
    private LocalDateTime createdAt;
}
