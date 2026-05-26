package com.chatapp.chatapp_backend.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MemberResponse {
    private Long id;
    private String userEmail;
    private String username;
    private String role;
    private LocalDateTime joinedAt;
}