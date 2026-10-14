package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.*;
import com.chatapp.chatapp_backend.entity.*;
import com.chatapp.chatapp_backend.mapper.*;
import com.chatapp.chatapp_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@Service @RequiredArgsConstructor
public class AppearanceService {
    private final UserRepository users;
    private final ServerRepository servers;
    private final UserMapper userMapper;
    private final ServerMapper serverMapper;
    private final PermissionService permissions;
    private final AppearanceMediaService media;
    public record ProfileAppearance(String bio, String accentColor) {}
    public record ServerAppearance(String accentColor) {}
    private String color(String color) {
        if (color == null || color.isBlank()) return null;
        if (!color.matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException("Cor inválida. Use #RRGGBB.");
        return color.toUpperCase();
    }
    private User user(String email) { return users.lockedByEmail(email).orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado")); }
    private Server server(long id, String email) { permissions.requireAdmin(id, email); return servers.findById(id).orElseThrow(() -> new IllegalArgumentException("Servidor não encontrado")); }
    private void cleanup(String previous, String next) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { media.delete(previous); }
            @Override public void afterCompletion(int status) { if (status != STATUS_COMMITTED) media.delete(next); }
        });
    }
    @Transactional public UserResponse profile(String email, ProfileAppearance request) {
        User user = user(email);
        String bio = request.bio() == null ? "" : request.bio().strip();
        if (bio.length() > 190 || bio.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '<' || c == '>')) throw new IllegalArgumentException("Sobre mim deve ter até 190 caracteres, sem tags ou controles.");
        user.setBio(bio); user.setAccentColor(color(request.accentColor()));
        return userMapper.toResponse(users.save(user));
    }
    @Transactional public ServerResponse server(long id, String email, ServerAppearance request) {
        Server server = server(id, email); server.setAccentColor(color(request.accentColor()));
        servers.save(server); return serverMapper.toResponse(server, server.getChannels());
    }
    @Transactional public UserResponse profileMedia(String email, String slot, MultipartFile file) throws IOException {
        User user = user(email);
        if (!java.util.Set.of("avatar", "banner", "background").contains(slot)) throw new IllegalArgumentException("Escolha avatar, banner ou background.");
        String previous = slot.equals("avatar") ? user.getAvatarUrl() : slot.equals("banner") ? user.getBannerUrl() : user.getBackgroundUrl();
        String next = file == null ? null : media.save(file, slot.equals("avatar"));
        cleanup(previous, next);
        switch (slot) { case "avatar" -> user.setAvatarUrl(next); case "banner" -> user.setBannerUrl(next); default -> user.setBackgroundUrl(next); }
        return userMapper.toResponse(users.save(user));
    }
    @Transactional public ServerResponse serverMedia(long id, String email, String slot, MultipartFile file) throws IOException {
        Server server = server(id, email);
        if (!java.util.Set.of("icon", "banner", "background").contains(slot)) throw new IllegalArgumentException("Escolha icon, banner ou background.");
        String previous = slot.equals("icon") ? server.getIconUrl() : slot.equals("banner") ? server.getBannerUrl() : server.getBackgroundUrl();
        String next = file == null ? null : media.save(file, slot.equals("icon"));
        cleanup(previous, next);
        switch (slot) { case "icon" -> server.setIconUrl(next); case "banner" -> server.setBannerUrl(next); default -> server.setBackgroundUrl(next); }
        servers.save(server); return serverMapper.toResponse(server, server.getChannels());
    }
}
