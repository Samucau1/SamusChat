package com.chatapp.chatapp_backend.websocket;
import com.chatapp.chatapp_backend.dto.MessageResponse;
public record MessageCreatedEvent(MessageResponse message) { }
