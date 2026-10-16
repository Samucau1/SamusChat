package com.chatapp.chatapp_backend.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String username;
    private String email;
    @lombok.ToString.Exclude
    private String password;
}
