package com.chatapp.chatapp_backend.factory;

import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.entity.Message;
import com.chatapp.chatapp_backend.entity.User;
import org.springframework.stereotype.Component;

@Component
public class MessageFactory {

    public Message createTextMessage(User sender, Channel channel, String content) {
        Message message = new Message();
        message.setSenderEmail(sender.getEmail());
        message.setSenderUsername(sender.getUsername());
        message.setChannel(channel);
        message.setContent(content.trim());
        return message;
    }

    public Message createAttachmentMessage(User sender, Channel channel,
                                           String content, String url, String type) {
        Message message = createTextMessage(sender, channel, content != null ? content : "");
        message.setAttachmentUrl(url);
        message.setAttachmentType(type);
        return message;
    }
}
