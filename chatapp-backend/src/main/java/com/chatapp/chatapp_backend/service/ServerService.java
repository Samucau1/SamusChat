package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.ChannelRequest;
import com.chatapp.chatapp_backend.dto.ChannelResponse;
import com.chatapp.chatapp_backend.dto.ServerRequest;
import com.chatapp.chatapp_backend.dto.ServerResponse;
import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.entity.Server;
import com.chatapp.chatapp_backend.entity.ServerMember;
import com.chatapp.chatapp_backend.factory.ServerFactory;
import com.chatapp.chatapp_backend.mapper.ServerMapper;
import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import com.chatapp.chatapp_backend.repository.ServerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServerService {

    private final ServerRepository serverRepository;
    private final ChannelRepository channelRepository;
    private final ServerMemberRepository serverMemberRepository;
    private final PermissionService permissionService;
    private final ServerFactory serverFactory;
    private final ServerMapper serverMapper;

    @Transactional
    public ServerResponse createServer(String ownerEmail, ServerRequest request) {
        validateServerName(request.getName());

        Server server = serverFactory.createServer(
                ownerEmail, request.getName(), request.getDescription()
        );
        serverRepository.save(server);

        Channel defaultChannel = serverFactory.createDefaultChannel(server);
        channelRepository.save(defaultChannel);

        serverMemberRepository.save(
                serverFactory.createMember(ownerEmail, server, "ADMIN")
        );

        return toResponse(server);
    }

    @Transactional(readOnly = true)
    public List<ServerResponse> getMyServers(String email) {
        return serverMemberRepository.findByUserEmail(email)
                .stream()
                .map(ServerMember::getServer)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ServerResponse getServer(Long serverId) {
        return toResponse(findServer(serverId));
    }

    @Transactional(readOnly = true)
    public ServerResponse getServer(String email, Long serverId) {
        permissionService.requireMember(serverId, email);
        return getServer(serverId);
    }

    @Transactional
    public ChannelResponse createChannel(String email, Long serverId, ChannelRequest request) {
        validateChannelName(request.getName());

        Server server = findServer(serverId);
        permissionService.requireAdmin(serverId, email);

        String type = request.getType() == null ? "TEXT" : request.getType();
        if (!type.equals("TEXT") && !type.equals("VOICE")) throw new IllegalArgumentException("Tipo de canal invalido");

        Channel channel = new Channel();
        channel.setName(request.getName().trim());
        channel.setType(type);
        channel.setServer(server);
        channelRepository.save(channel);

        return serverMapper.toChannelResponse(channel);
    }

    @Transactional
    public String joinServer(String email, Long serverId) {
        Server server = findServer(serverId);

        if (serverMemberRepository.existsByServerIdAndUserEmail(serverId, email)) {
            throw new RuntimeException("Voce ja e membro deste servidor");
        }

        serverMemberRepository.save(
                serverFactory.createMember(email, server, "USER")
        );

        return "Entrou no servidor: " + server.getName();
    }

    private void validateServerName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 255) {
            throw new RuntimeException("Nome do servidor e obrigatorio");
        }
    }

    private void validateChannelName(String name) {
        if (name == null || name.isBlank() || name.trim().length() > 255) {
            throw new RuntimeException("Nome do canal e obrigatorio");
        }
    }

    private Server findServer(Long serverId) {
        return serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor nao encontrado"));
    }

    private ServerResponse toResponse(Server server) {
        List<Channel> channels = channelRepository.findByServerId(server.getId());
        return serverMapper.toResponse(server, channels);
    }
}
