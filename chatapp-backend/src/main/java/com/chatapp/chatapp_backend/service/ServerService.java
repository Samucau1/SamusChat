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
    private final ServerInviteCodes inviteCodes;

    @Transactional
    public ServerResponse createServer(String ownerEmail, ServerRequest request) {
        validateServerName(request.getName());

        Server server = serverFactory.createServer(
                ownerEmail, request.getName(), request.getDescription()
        );
        inviteCodes.ensureCode(server);
        serverRepository.save(server);

        Channel defaultChannel = serverFactory.createDefaultChannel(server);
        channelRepository.save(defaultChannel);

        serverMemberRepository.save(
                serverFactory.createMember(ownerEmail, server, "ADMIN")
        );

        return toResponse(server, ownerEmail);
    }

    @Transactional
    public List<ServerResponse> getMyServers(String email) {
        return serverMemberRepository.findByUserEmail(email)
                .stream()
                .map(ServerMember::getServer)
                .map(server -> toResponse(server, email))
                .collect(Collectors.toList());
    }

    @Transactional
    public ServerResponse getServer(String email, Long serverId) {
        permissionService.requireMember(serverId, email);
        return toResponse(findServer(serverId), email);
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
    public String joinServer(String email, String code) {
        if (!ServerInviteCodes.valid(code)) throw new IllegalArgumentException("Use um codigo de 3 caracteres.");
        Server server = serverRepository.findByInviteCode(code).orElseThrow(() -> new IllegalArgumentException("Codigo de servidor invalido."));
        return addMember(email, server);
    }

    /** Compatibility with the numeric invite endpoint used by installed clients. */
    @Transactional
    public String joinServerByLegacyId(String email, Long serverId) {
        return addMember(email, findServer(serverId));
    }

    private String addMember(String email, Server server) {
        Long serverId = server.getId();

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

    private ServerResponse toResponse(Server server, String email) {
        List<Channel> channels = channelRepository.findByServerId(server.getId());
        ServerResponse response = serverMapper.toResponse(server, channels);
        String role = permissionService.getRole(server.getId(), email);
        response.setMyRole(role);
        if ("ADMIN".equals(role)) {
            inviteCodes.ensureCode(server);
            response.setInviteCode(server.getInviteCode());
        }
        return response;
    }
}
