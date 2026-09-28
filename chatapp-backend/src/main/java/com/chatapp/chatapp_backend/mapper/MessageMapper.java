package com.chatapp.chatapp_backend.mapper;

import com.chatapp.chatapp_backend.dto.MessageResponse;
import com.chatapp.chatapp_backend.entity.Message;
import org.springframework.stereotype.Component;

@Component
public class MessageMapper {

    public MessageResponse toResponse(Message message) {
        if (message == null) {
            return null;
        }

        return MessageResponse.builder()
                .id(message.getId())
                .content(message.getContent())
                .senderEmail(message.getSenderEmail())
                .senderUsername(message.getSenderUsername())
                .channelId(message.getChannel().getId())
                .attachmentUrl(message.getAttachmentUrl())
                .attachmentType(message.getAttachmentType())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
