package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.MessageRequest;
import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/channels/{channelId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    @PostMapping
    public ResponseEntity<MessageResponse> sendMessage(
            Authentication auth,
            @PathVariable Long channelId,
            @RequestBody MessageRequest request) {
        return ResponseEntity.ok(messageService.sendMessage(auth.getName(), channelId, request));
    }

    @GetMapping
    public ResponseEntity<List<MessageResponse>> getMessages(
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(messageService.getMessages(channelId, page, size));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(
            Authentication auth,
            @PathVariable Long messageId) {
        messageService.deleteMessage(auth.getName(), messageId);
        return ResponseEntity.noContent().build();
    }
}
