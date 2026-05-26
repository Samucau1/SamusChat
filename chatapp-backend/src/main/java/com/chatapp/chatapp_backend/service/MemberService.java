package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.MemberResponse;
import com.chatapp.chatapp_backend.dto.UpdateRoleRequest;
import com.chatapp.chatapp_backend.entity.ServerMember;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import com.chatapp.chatapp_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MemberService {

    private final ServerMemberRepository serverMemberRepository;
    private final UserRepository userRepository;
    private final PermissionService permissionService;

    // Lista todos os membros de um servidor
    public List<MemberResponse> getMembers(Long serverId, String requesterEmail) {
        permissionService.requireMember(serverId, requesterEmail);

        return serverMemberRepository.findByServerId(serverId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // Atualiza o cargo de um membro (apenas ADMIN pode)
    public MemberResponse updateRole(Long serverId, String requesterEmail,
                                     UpdateRoleRequest request) {
        permissionService.requireAdmin(serverId, requesterEmail);

        // Valida o cargo informado
        if (!List.of("ADMIN", "MOD", "USER").contains(request.getRole())) {
            throw new RuntimeException("Cargo inválido. Use: ADMIN, MOD ou USER");
        }

        ServerMember member = serverMemberRepository
                .findByServerIdAndUserEmail(serverId, request.getUserEmail())
                .orElseThrow(() -> new RuntimeException("Membro não encontrado"));

        member.setRole(request.getRole());
        serverMemberRepository.save(member);

        return toResponse(member);
    }

    // Remove um membro do servidor (ADMIN pode remover qualquer um, USER pode sair)
    public void removeMember(Long serverId, String requesterEmail, String targetEmail) {

        // Usuário pode sair por conta própria
        if (requesterEmail.equals(targetEmail)) {
            ServerMember member = serverMemberRepository
                    .findByServerIdAndUserEmail(serverId, requesterEmail)
                    .orElseThrow(() -> new RuntimeException("Você não é membro"));
            serverMemberRepository.delete(member);
            return;
        }

        // Para remover outro usuário, precisa ser ADMIN
        permissionService.requireAdmin(serverId, requesterEmail);

        ServerMember member = serverMemberRepository
                .findByServerIdAndUserEmail(serverId, targetEmail)
                .orElseThrow(() -> new RuntimeException("Membro não encontrado"));

        serverMemberRepository.delete(member);
    }

    private MemberResponse toResponse(ServerMember member) {
        MemberResponse response = new MemberResponse();
        response.setId(member.getId());
        response.setUserEmail(member.getUserEmail());
        response.setRole(member.getRole());
        response.setJoinedAt(member.getJoinedAt());

        // Busca o username do usuário
        userRepository.findByEmail(member.getUserEmail())
                .ifPresent(u -> response.setUsername(u.getUsername()));

        return response;
    }
}