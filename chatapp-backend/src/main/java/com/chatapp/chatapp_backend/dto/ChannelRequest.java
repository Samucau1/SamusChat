package com.chatapp.chatapp_backend.dto;

import lombok.Data;

@Data
public class ChannelRequest {
    private String name;
    private String type; // TEXT ou VOICE
}