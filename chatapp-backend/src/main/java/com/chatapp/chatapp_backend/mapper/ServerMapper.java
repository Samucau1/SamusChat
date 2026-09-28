package com.chatapp.chatapp_backend.mapper;

import com.chatapp.chatapp_backend.dto.ChannelResponse;
import com.chatapp.chatapp_backend.dto.ServerResponse;
import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.entity.Server;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ServerMapper {

    public ServerResponse toResponse(Server server, List<Channel> channels) {
        if (server == null) {
            return null;
        }

        return ServerResponse.builder()
                .id(server.getId())
                .name(server.getName())
                .description(server.getDescription())
                .ownerEmail(server.getOwnerEmail())
                .createdAt(server.getCreatedAt())
                .channels(channels.stream()
                        .map(this::toChannelResponse)
                        .collect(Collectors.toList()))
                .build();
    }

    public ChannelResponse toChannelResponse(Channel channel) {
        if (channel == null) {
            return null;
        }

        return ChannelResponse.builder()
                .id(channel.getId())
                .name(channel.getName())
                .type(channel.getType())
                .createdAt(channel.getCreatedAt())
                .build();
    }
}
