package com.chatapp.chatapp_backend.dto;

import lombok.Data;

@Data
public class UpdateRoleRequest {
    private String userEmail;
    private String role; // ADMIN, MOD, USER
}