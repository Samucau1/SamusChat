package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.ApiResponse;
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
    public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
            Authentication auth,
            @PathVariable Long channelId,
            @RequestBody MessageRequest request) {
        MessageResponse response = messageService.sendMessage(auth.getName(), channelId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(
            Authentication auth,
            @PathVariable Long channelId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        List<MessageResponse> messages = messageService.getMessages(auth.getName(), channelId, page, size);
        return ResponseEntity.ok(ApiResponse.success(messages));
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<ApiResponse<Void>> deleteMessage(
            Authentication auth,
            @PathVariable Long messageId) {
        messageService.deleteMessage(auth.getName(), messageId);
        return ResponseEntity.ok(ApiResponse.success(null, "Mensagem deletada com sucesso"));
    }
}
