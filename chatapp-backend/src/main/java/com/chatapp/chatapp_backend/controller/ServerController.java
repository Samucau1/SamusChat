package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.*;
import com.chatapp.chatapp_backend.service.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    // POST /api/servers — criar servidor
    @PostMapping
    public ResponseEntity<ServerResponse> createServer(
            Authentication auth,
            @RequestBody ServerRequest request) {
        return ResponseEntity.ok(serverService.createServer(auth.getName(), request));
    }

    // GET /api/servers — listar meus servidores
    @GetMapping
    public ResponseEntity<List<ServerResponse>> getMyServers(Authentication auth) {
        return ResponseEntity.ok(serverService.getMyServers(auth.getName()));
    }

    // GET /api/servers/{id} — buscar servidor por ID
    @GetMapping("/{id}")
    public ResponseEntity<ServerResponse> getServer(
            Authentication auth,
            @PathVariable Long id) {
        return ResponseEntity.ok(serverService.getServer(auth.getName(), id));
    }

    // POST /api/servers/{id}/join — entrar em um servidor
    @PostMapping("/{id}/join")
    public ResponseEntity<String> joinServer(
            Authentication auth,
            @PathVariable Long id) {
        return ResponseEntity.ok(serverService.joinServer(auth.getName(), id));
    }

    // POST /api/servers/{id}/channels — criar canal
    @PostMapping("/{id}/channels")
    public ResponseEntity<ChannelResponse> createChannel(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody ChannelRequest request) {
        return ResponseEntity.ok(serverService.createChannel(auth.getName(), id, request));
    }
}
