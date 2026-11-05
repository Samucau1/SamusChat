package com.chatapp.chatapp_backend.mapper;

import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.entity.Message;
import org.springframework.stereotype.Component;

@Component
@lombok.RequiredArgsConstructor
public class MessageMapper {
    private final com.chatapp.chatapp_backend.repository.UserRepository users;

    public java.util.List<MessageResponse> toResponses(java.util.List<Message> messages) {
        if (messages.isEmpty()) return java.util.List.of();

        var emails = messages.stream().map(Message::getSenderEmail).distinct().toList();
        var avatars = new java.util.HashMap<String, String>();
        users.findByEmailIn(emails).forEach(user -> avatars.put(user.getEmail(), user.getAvatarUrl()));
        return messages.stream()
                .map(message -> toResponse(message, avatars.get(message.getSenderEmail())))
                .toList();
    }

    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }

        return toResponse(message, users.findByEmail(message.getSenderEmail())
                .map(com.chatapp.chatapp_backend.entity.User::getAvatarUrl).orElse(null));
    }

    private MessageResponse toResponse(Message message, String avatarUrl) {
        return MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .senderEmail(message.getSenderEmail())
                .senderUsername(message.getSenderUsername())
                .senderAvatarUrl(avatarUrl)
                .channelId(message.getChannel().getId())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
