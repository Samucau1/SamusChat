package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.*;
import com.chatapp.chatapp_backend.entity.DirectMessage;
import com.chatapp.chatapp_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/direct-messages")
@RequiredArgsConstructor
public class DirectMessageController {
    private final DirectMessageRepository messages;
    private final UserRepository users;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final com.chatapp.chatapp_backend.security.PublicIdentityService identities;

    public record Unread(String senderEmail, long count, long lastId) {}

    @GetMapping("/unread")
    public ApiResponse<List<Unread>> unread(Authentication auth) {
        return ApiResponse.success(messages.unread(auth.getName()).stream()
            .map(item -> new Unread(item.getSenderEmail(), item.getCount(), item.getLastId())).toList());
    }

    @PostMapping("/read")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Integer> read(Authentication auth, @RequestParam String contact, @RequestParam long throughId) {
        contact = identities.resolve(contact);
        validateContact(auth.getName(), contact);
        if (throughId <= 0 || !messages.existsByIdAndSenderEmailAndRecipientEmail(throughId, contact, auth.getName()))
            throw new IllegalArgumentException("Mensagem indisponivel nesta conversa");
        return ApiResponse.success(messages.markRead(auth.getName(), contact, throughId));
    }

    private void validateContact(String me, String contact) {
        if (me.equals(contact)) throw new IllegalArgumentException("Escolha outro contato");
        if (!users.existsByEmail(contact)) throw new IllegalArgumentException("Contato não encontrado");
    }

    @GetMapping
    public ApiResponse<List<DirectMessage>> history(Authentication auth, @RequestParam String contact,
            @RequestParam(defaultValue = "0") int page) {
        contact = identities.resolve(contact);
        validateContact(auth.getName(), contact);
        if (page < 0) throw new IllegalArgumentException("Página inválida");
        var history = messages.conversation(auth.getName(), contact, PageRequest.of(page, 50));
        var avatars = new java.util.HashMap<String, String>();
        for (var message : history) {
            String avatar = avatars.computeIfAbsent(message.getSenderEmail(), email -> users.findByEmail(email).map(com.chatapp.chatapp_backend.entity.User::getAvatarUrl).orElse(""));
            message.setSenderAvatarUrl(avatar.isEmpty() ? null : avatar);
        }
        return ApiResponse.success(history);
    }

    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<DirectMessage> send(Authentication auth, @RequestParam String contact, @RequestBody MessageRequest request) {
        contact = identities.resolve(contact);
        validateContact(auth.getName(), contact);
        String text = request.getContent();
        if (text == null || text.isBlank() || text.length() > 2000)
            throw new IllegalArgumentException("A mensagem deve ter entre 1 e 2000 caracteres");
        var sender = users.findByEmail(auth.getName()).orElseThrow(() -> new SecurityException("Conta não encontrada"));
        var message = new DirectMessage();
        message.setSenderEmail(sender.getEmail());
        message.setSenderUsername(sender.getUsername());
        message.setRecipientEmail(contact);
        message.setContent(text.trim());
        var saved = messages.save(message);
        saved.setSenderAvatarUrl(sender.getAvatarUrl());
        events.publishEvent(new com.chatapp.chatapp_backend.websocket.DirectMessageCreatedEvent(saved));
        return ApiResponse.success(saved);
    }
}
