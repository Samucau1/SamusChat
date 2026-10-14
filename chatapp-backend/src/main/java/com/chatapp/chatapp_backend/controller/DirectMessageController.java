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

    private void validateContact(String me, String contact) {
        if (me.equals(contact)) throw new IllegalArgumentException("Escolha outro contato");
        if (!users.existsByEmail(contact)) throw new IllegalArgumentException("Contato não encontrado");
    }

    @GetMapping
    public ApiResponse<List<DirectMessage>> history(Authentication auth, @RequestParam String contact,
            @RequestParam(defaultValue = "0") int page) {
        validateContact(auth.getName(), contact);
        if (page < 0) throw new IllegalArgumentException("Página inválida");
        return ApiResponse.success(messages.conversation(auth.getName(), contact, PageRequest.of(page, 50)));
    }

    @PostMapping
    public ApiResponse<DirectMessage> send(Authentication auth, @RequestParam String contact, @RequestBody MessageRequest request) {
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
        return ApiResponse.success(messages.save(message));
    }
}
