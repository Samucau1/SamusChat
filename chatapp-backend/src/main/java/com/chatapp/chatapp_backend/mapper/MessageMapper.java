package com.chatapp.chatapp_backend.mapper;

import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.entity.Message;
import org.springframework.stereotype.Component;

@Component
@lombok.RequiredArgsConstructor
public class MessageMapper {
    private final com.chatapp.chatapp_backend.repository.UserRepository users;

    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }

        return MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .senderEmail(message.getSenderEmail())
                .senderUsername(message.getSenderUsername())
                .senderAvatarUrl(users.findByEmail(message.getSenderEmail()).map(com.chatapp.chatapp_backend.entity.User::getAvatarUrl).orElse(null))
                .channelId(message.getChannel().getId())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
