package com.chatapp.chatapp_backend.websocket;

import com.chatapp.chatapp_backend.entity.DirectMessage;

public record DirectMessageCreatedEvent(DirectMessage message) { }
