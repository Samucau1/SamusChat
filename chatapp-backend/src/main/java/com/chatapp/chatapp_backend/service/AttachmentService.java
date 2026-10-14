package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.entity.Message;
import com.chatapp.chatapp_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.nio.file.*;

@Service @RequiredArgsConstructor
public class AttachmentService {
    private final FileStorageService storage;
    private final MessageService messages;
    private final MessageRepository repository;
    private final ChannelRepository channels;
    private final UserRepository users;
    private final PermissionService permissions;
    @Transactional
    public MessageResponse upload(String email, Long channelId, MultipartFile file, String content) throws IOException {
        var channel = channels.findById(channelId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        permissions.requireMember(channel.getServer().getId(), email);
        users.lockedByEmail(email).orElseThrow(() -> new SecurityException("Conta invalida"));
        if (repository.countBySenderEmailAndAttachmentUrlIsNotNull(email) >= 500 || repository.attachmentBytes(email) + file.getSize() > 250L * 1024 * 1024) throw new IllegalArgumentException("Limite de anexos da conta atingido");
        String url = storage.saveFile(file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) storage.deleteFile(url); }
        });
        var response = messages.sendMessageWithAttachment(email, channelId, content, url, file.getContentType().startsWith("image/") ? "IMAGE" : "FILE");
        var saved = repository.findById(response.getId()).orElseThrow();
        saved.setAttachmentSize(Files.size(storage.file(saved.getAttachmentName())));
        return response;
    }
    private Message permitted(String email, String filename) {
        storage.file(filename);
        var message = repository.findFirstByAttachmentName(filename).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        permissions.requireMember(message.getChannel().getServer().getId(), email); return message;
    }
    @Transactional(readOnly = true)
    public Path download(String email, String filename) {
        permitted(email, filename); Path file = storage.file(filename);
        if (!Files.isRegularFile(file)) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return file;
    }
    @Transactional
    public void delete(String email, String url) {
        var message = permitted(email, storage.filename(url));
        if (!message.getSenderEmail().equals(email) && !permissions.isModOrAdmin(message.getChannel().getServer().getId(), email)) throw new org.springframework.security.access.AccessDeniedException("Acesso negado");
        String original = message.getAttachmentUrl();
        message.setAttachmentUrl(null); message.setAttachmentType(null); message.setAttachmentName(null); message.setAttachmentSize(null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { storage.deleteFile(original); }
        });
    }
}
