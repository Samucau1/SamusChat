package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.MessageRequest;
import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.entity.Message;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.repository.MessageRepository;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import com.chatapp.chatapp_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final NotificationService notificationService;
    private final ServerMemberRepository serverMemberRepository;

    public MessageResponse sendMessage(String senderEmail, Long channelId, MessageRequest request) {
        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Canal nao encontrado"));

        User user = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));

        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new RuntimeException("Mensagem nao pode estar vazia");
        }

        if (request.getContent().length() > 2000) {
            throw new RuntimeException("Mensagem muito longa");
        }

        Message message = new Message();
        message.setContent(request.getContent().trim());
        message.setSenderEmail(senderEmail);
        message.setSenderUsername(user.getUsername());
        message.setChannel(channel);

        messageRepository.save(message);

        notifyServerMembers(channel, user, request.getContent().trim(), senderEmail);

        return toResponse(message);
    }

    public MessageResponse sendMessageWithAttachment(
            String senderEmail,
            Long channelId,
            String content,
            String attachmentUrl,
            String attachmentType) {

        Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Canal nao encontrado"));

        User user = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));

        if ((content == null || content.isBlank()) && attachmentUrl == null) {
            throw new RuntimeException("Mensagem precisa ter conteudo ou anexo");
        }

        if (content != null && content.length() > 2000) {
            throw new RuntimeException("Mensagem muito longa");
        }

        Message message = new Message();
        message.setContent(content != null && !content.isBlank() ? content.trim() : null);
        message.setSenderEmail(senderEmail);
        message.setSenderUsername(user.getUsername());
        message.setAttachmentUrl(attachmentUrl);
        message.setAttachmentType(attachmentType);
        message.setChannel(channel);

        messageRepository.save(message);

        String notificationBody = message.getContent() != null ? message.getContent() : "Enviou um anexo";
        notifyServerMembers(channel, user, notificationBody, senderEmail);

        return toResponse(message);
    }

    public List<MessageResponse> getMessages(Long channelId, int page, int size) {
        channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Canal nao encontrado"));

        Page<Message> messages = messageRepository
                .findByChannelIdOrderByCreatedAtDesc(channelId, PageRequest.of(page, size));

        List<Message> list = new java.util.ArrayList<>(messages.getContent());
        java.util.Collections.reverse(list);

        return list.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public void deleteMessage(String email, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Mensagem nao encontrada"));

        Long serverId = message.getChannel().getServer().getId();

        boolean isAuthor = message.getSenderEmail().equals(email);
        boolean isModOrAdmin = permissionService.isModOrAdmin(serverId, email);

        if (!isAuthor && !isModOrAdmin) {
            throw new RuntimeException("Voce nao tem permissao para deletar esta mensagem");
        }

        messageRepository.delete(message);
    }

    private MessageResponse toResponse(Message message) {
        MessageResponse response = new MessageResponse();
        response.setId(message.getId());
        response.setContent(message.getContent());
        response.setSenderEmail(message.getSenderEmail());
        response.setSenderUsername(message.getSenderUsername());
        response.setChannelId(message.getChannel().getId());
        response.setAttachmentUrl(message.getAttachmentUrl());
        response.setAttachmentType(message.getAttachmentType());
        response.setCreatedAt(message.getCreatedAt());
        return response;
    }

    private void notifyServerMembers(Channel channel, User user, String body, String senderEmail) {
        Long serverId = channel.getServer().getId();
        List<String> memberEmails = serverMemberRepository
                .findByServerId(serverId)
                .stream()
                .map(member -> member.getUserEmail())
                .collect(Collectors.toList());

        notificationService.sendToUsers(
                memberEmails,
                user.getUsername(),
                body,
                String.valueOf(channel.getId()),
                senderEmail
        );
    }
}
