package com.chatapp.chatapp_backend.dto;

import lombok.Data;

@Data
public class DeviceTokenRequest {
    private String fcmToken;
    private String deviceModel;
}
