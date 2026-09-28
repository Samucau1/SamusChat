package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.entity.ServerMember;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PermissionService {
    private final ServerMemberRepository serverMemberRepository;




    // Retorna o cargo do usuário no servidor
    public String getRole(Long serverId, String email) {
        return serverMemberRepository
                .findByServerIdAndUserEmail(serverId, email)
                .map(ServerMember::getRole)
                .orElse(null); // null = não é membro
    }

    // Verifica se é membro do servidor
    public boolean isMember(Long serverId, String email) {
        return serverMemberRepository.existsByServerIdAndUserEmail(serverId, email);
    }

    // Verifica se é ADMIN
    public boolean isAdmin(Long serverId, String email) {
        String role = getRole(serverId, email);
        return "ADMIN".equals(role);
    }

    // Verifica se é MOD ou ADMIN
    public boolean isModOrAdmin(Long serverId, String email) {
        String role = getRole(serverId, email);
        return "ADMIN".equals(role) || "MOD".equals(role);
    }

    // Lança exceção se não for membro
    public void requireMember(Long serverId, String email) {
        if (!isMember(serverId, email)) {
            throw new RuntimeException("Você não é membro deste servidor");
        }
    }

    // Lança exceção se não for ADMIN
    public void requireAdmin(Long serverId, String email) {
        if (!isAdmin(serverId, email)) {
            throw new RuntimeException("Apenas administradores podem fazer isso");
        }
    }

    // Lança exceção se não for MOD ou ADMIN
    public void requireModOrAdmin(Long serverId, String email) {
        if (!isModOrAdmin(serverId, email)) {
            throw new RuntimeException("Você não tem permissão para isso");
        }
    }
}
