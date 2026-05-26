package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.ChannelRequest;
import com.chatapp.chatapp_backend.dto.ChannelResponse;
import com.chatapp.chatapp_backend.dto.ServerRequest;
import com.chatapp.chatapp_backend.dto.ServerResponse;
import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.entity.Server;
import com.chatapp.chatapp_backend.entity.ServerMember;
import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import com.chatapp.chatapp_backend.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final ChannelRepository channelRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final PermissionService permissionService;

    public ServerResponse createServer(String ownerEmail, ServerRequest request) {
        Server server = new Server();
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setOwnerEmail(ownerEmail);
        serverRepository.save(server);

        Channel general = new Channel();
        general.setName("geral");
        general.setType("TEXT");
        general.setServer(server);
        channelRepository.save(general);

        ServerMember member = new ServerMember();
        member.setUserEmail(ownerEmail);
        member.setRole("ADMIN");
        member.setServer(server);
        serverMemberRepository.save(member);

        return toResponse(server);
    }

    public List<ServerResponse> getMyServers(String email) {
        return serverRepository.findByOwnerEmail(email)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public ServerResponse getServer(Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor nao encontrado"));
        return toResponse(server);
    }

    public ServerResponse getServer(String email, Long serverId) {
        permissionService.requireMember(serverId, email);
        return getServer(serverId);
    }

    public ChannelResponse createChannel(String email, Long serverId, ChannelRequest request) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor nao encontrado"));

        permissionService.requireAdmin(serverId, email);

        Channel channel = new Channel();
        channel.setName(request.getName());
        channel.setType(request.getType() != null ? request.getType() : "TEXT");
        channel.setServer(server);
        channelRepository.save(channel);

        return toChannelResponse(channel);
    }

    public String joinServer(String email, Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor nao encontrado"));

        if (serverMemberRepository.existsByServerIdAndUserEmail(serverId, email)) {
            throw new RuntimeException("Voce ja e membro deste servidor");
        }

        ServerMember member = new ServerMember();
        member.setUserEmail(email);
        member.setRole("USER");
        member.setServer(server);
        serverMemberRepository.save(member);

        return "Entrou no servidor: " + server.getName();
    }

    private ServerResponse toResponse(Server server) {
        ServerResponse response = new ServerResponse();
        response.setId(server.getId());
        response.setName(server.getName());
        response.setDescription(server.getDescription());
        response.setOwnerEmail(server.getOwnerEmail());
        response.setCreatedAt(server.getCreatedAt());

        List<Channel> channels = channelRepository.findByServerId(server.getId());
        response.setChannels(channels.stream()
                .map(this::toChannelResponse)
                .collect(Collectors.toList()));

        return response;
    }

    private ChannelResponse toChannelResponse(Channel channel) {
        ChannelResponse response = new ChannelResponse();
        response.setId(channel.getId());
        response.setName(channel.getName());
        response.setType(channel.getType());
        response.setCreatedAt(channel.getCreatedAt());
        return response;
    }
}
