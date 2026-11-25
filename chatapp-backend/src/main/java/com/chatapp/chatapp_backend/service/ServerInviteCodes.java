package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.entity.Server;
import com.chatapp.chatapp_backend.repository.ServerRepository;
import com.chatapp.chatapp_backend.repository.ServerInviteCodeLockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;

@Service
@RequiredArgsConstructor
public class ServerInviteCodes {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%*+-_";
    private final SecureRandom random = new SecureRandom();
    private final ServerRepository servers;
    private final ServerInviteCodeLockRepository allocation;

    public static boolean valid(String code) {
        return code != null && code.length() == 3 && code.chars().allMatch(c -> ALPHABET.indexOf(c) >= 0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void ensureCode(Server server) {
        if (server.getInviteCode() != null) return;
        if (allocation.lockAllocation() == null) throw new IllegalStateException("Inicializacao dos codigos pendente");
        // Another transaction may have allocated this existing server while we waited.
        if (server.getId() != null) {
            String existing = servers.inviteCodeOf(server.getId());
            if (existing != null) { server.setInviteCode(existing); return; }
        }
        for (int attempt = 0; attempt < 256; attempt++) {
            StringBuilder code = new StringBuilder(3);
            for (int i = 0; i < 3; i++) code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
            if (!servers.existsByInviteCode(code.toString())) {
                server.setInviteCode(code.toString());
                return;
            }
        }
        throw new IllegalStateException("Nao foi possivel reservar um codigo. Tente novamente.");
    }
}
