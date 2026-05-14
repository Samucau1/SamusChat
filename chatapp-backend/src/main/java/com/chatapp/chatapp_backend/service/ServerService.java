package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.*;
import com.chatapp.chatapp_backend.entity.*;
import com.chatapp.chatapp_backend.repository.*;
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

    // Cria um servidor e já adiciona o criador como ADMIN
    public ServerResponse createServer(String ownerEmail, ServerRequest request) {
        Server server = new Server();
        server.setName(request.getName());
        server.setDescription(request.getDescription());
        server.setOwnerEmail(ownerEmail);
        serverRepository.save(server);

        // Cria canal geral automaticamente
        Channel general = new Channel();
        general.setName("geral");
        general.setType("TEXT");
        general.setServer(server);
        channelRepository.save(general);

        // Adiciona o criador como ADMIN
        ServerMember member = new ServerMember();
        member.setUserEmail(ownerEmail);
        member.setRole("ADMIN");
        member.setServer(server);
        serverMemberRepository.save(member);

        return toResponse(server);
    }

    // Lista todos os servidores que o usuário participa
    public List<ServerResponse> getMyServers(String email) {
        return serverMemberRepository.findByUserEmail(email)
                .stream()
                .map(ServerMember::getServer)
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Busca um servidor pelo ID
    public ServerResponse getServer(String email, Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor não encontrado"));

        if (!serverMemberRepository.existsByServerIdAndUserEmail(serverId, email)) {
            throw new RuntimeException("Você não participa deste servidor");
        }

        return toResponse(server);
    }

    // Cria um canal dentro de um servidor
    public ChannelResponse createChannel(String email, Long serverId, ChannelRequest request) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor não encontrado"));

        // Só o dono pode criar canais
        if (!server.getOwnerEmail().equals(email)) {
            throw new RuntimeException("Apenas o dono pode criar canais");
        }

        Channel channel = new Channel();
        channel.setName(request.getName());
        channel.setType(request.getType() != null ? request.getType() : "TEXT");
        channel.setServer(server);
        channelRepository.save(channel);

        return toChannelResponse(channel);
    }

    // Entrar em um servidor
    public String joinServer(String email, Long serverId) {
        Server server = serverRepository.findById(serverId)
                .orElseThrow(() -> new RuntimeException("Servidor não encontrado"));

        if (serverMemberRepository.existsByServerIdAndUserEmail(serverId, email)) {
            throw new RuntimeException("Você já é membro deste servidor");
        }

        ServerMember member = new ServerMember();
        member.setUserEmail(email);
        member.setRole("USER");
        member.setServer(server);
        serverMemberRepository.save(member);

        return "Entrou no servidor: " + server.getName();
    }

    // Conversão de entidade para DTO
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
