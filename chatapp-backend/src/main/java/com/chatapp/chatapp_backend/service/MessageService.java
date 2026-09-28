package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.MessageRequest;
import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.entity.Message;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.factory.MessageFactory;
import com.chatapp.chatapp_backend.mapper.MessageMapper;
import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.repository.MessageRepository;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.chatapp.chatapp_backend.strategy.NotificationStrategy;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import com.chatapp.chatapp_backend.websocket.MessageCreatedEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChannelRepository channelRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;
    private final ServerMemberRepository serverMemberRepository;
    private final MessageFactory messageFactory;
    private final MessageMapper messageMapper;
    private final NotificationStrategy notificationStrategy;
    private final ApplicationEventPublisher events;

    public MessageService(
            MessageRepository messageRepository,
            ChannelRepository channelRepository,
            UserRepository userRepository,
            PermissionService permissionService,
            ServerMemberRepository serverMemberRepository,
            MessageFactory messageFactory,
            MessageMapper messageMapper,
            @Qualifier("pushStrategy") NotificationStrategy notificationStrategy,
            ApplicationEventPublisher events) {
        this.events = events;
        this.messageRepository = messageRepository;
        this.channelRepository = channelRepository;
        this.userRepository = userRepository;
        this.permissionService = permissionService;
        this.serverMemberRepository = serverMemberRepository;
        this.messageFactory = messageFactory;
        this.messageMapper = messageMapper;
        this.notificationStrategy = notificationStrategy;
    }

    @Transactional
    public MessageResponse sendMessage(String senderEmail, Long channelId, MessageRequest request) {
        validateMessageContent(request.getContent());

        Channel channel = findChannel(channelId);
        permissionService.requireMember(channel.getServer().getId(), senderEmail);
        User sender = findUser(senderEmail);

        Message message = messageFactory.createTextMessage(sender, channel, request.getContent());
        messageRepository.save(message);

        notifyChannelMembers(channel, sender, message.getContent());

        MessageResponse response = messageMapper.toResponse(message);
        events.publishEvent(new MessageCreatedEvent(response));
        return response;
    }

    @Transactional
    public MessageResponse sendMessageWithAttachment(String senderEmail, Long channelId,
                                                     String content, String attachmentUrl,
                                                     String attachmentType) {
        validateAttachmentMessage(content, attachmentUrl);

        Channel channel = findChannel(channelId);
        permissionService.requireMember(channel.getServer().getId(), senderEmail);
        User sender = findUser(senderEmail);

        Message message = messageFactory.createAttachmentMessage(
                sender, channel, content, attachmentUrl, attachmentType
        );
        messageRepository.save(message);

        String notificationBody = message.getContent() != null && !message.getContent().isBlank()
                ? message.getContent()
                : "Enviou um anexo";
        notifyChannelMembers(channel, sender, notificationBody);

        MessageResponse response = messageMapper.toResponse(message);
        events.publishEvent(new MessageCreatedEvent(response));
        return response;
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> getMessages(String email, Long channelId, int page, int size) {
        Channel channel = findChannel(channelId);
        permissionService.requireMember(channel.getServer().getId(), email);
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Paginacao invalida");

        List<Message> messages = new ArrayList<>(messageRepository
                .findByChannelIdOrderByCreatedAtDesc(channelId, PageRequest.of(page, size))
                .getContent());

        Collections.reverse(messages);

        return messages.stream()
                .map(messageMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteMessage(String email, Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Mensagem nao encontrada"));

        Long serverId = message.getChannel().getServer().getId();
        boolean isAuthor = message.getSenderEmail().equals(email);
        boolean isModerator = permissionService.isModOrAdmin(serverId, email);

        if (!isAuthor && !isModerator) {
            throw new RuntimeException("Voce nao tem permissao para deletar esta mensagem");
        }

        messageRepository.delete(message);
    }

    private void validateMessageContent(String content) {
        if (content == null || content.isBlank()) {
            throw new RuntimeException("Mensagem nao pode estar vazia");
        }
        validateMessageLength(content);
    }

    private void validateAttachmentMessage(String content, String attachmentUrl) {
        if ((content == null || content.isBlank()) && attachmentUrl == null) {
            throw new RuntimeException("Mensagem precisa ter conteudo ou anexo");
        }
        if (content != null) {
            validateMessageLength(content);
        }
    }

    private void validateMessageLength(String content) {
        if (content.length() > 2000) {
            throw new RuntimeException("Mensagem muito longa. Maximo: 2000 caracteres");
        }
    }

    private Channel findChannel(Long channelId) {
        return channelRepository.findById(channelId)
                .orElseThrow(() -> new RuntimeException("Canal nao encontrado"));
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));
    }

    private void notifyChannelMembers(Channel channel, User sender, String content) {
        Long serverId = channel.getServer().getId();

        List<String> recipients = serverMemberRepository
                .findByServerId(serverId)
                .stream()
                .map(member -> member.getUserEmail())
                .collect(Collectors.toList());

        notificationStrategy.notify(
                recipients,
                sender.getUsername(),
                content,
                String.valueOf(channel.getId()),
                sender.getEmail()
        );
    }
}
