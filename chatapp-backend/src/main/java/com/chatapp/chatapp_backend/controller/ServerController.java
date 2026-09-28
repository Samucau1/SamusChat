package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.ApiResponse;
import com.chatapp.chatapp_backend.dto.ChannelRequest;
import com.chatapp.chatapp_backend.dto.ChannelResponse;
import com.chatapp.chatapp_backend.dto.ServerRequest;
import com.chatapp.chatapp_backend.dto.ServerResponse;
import com.chatapp.chatapp_backend.service.ServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/servers")
@RequiredArgsConstructor
public class ServerController {

    private final ServerService serverService;

    @PostMapping
    public ResponseEntity<ApiResponse<ServerResponse>> createServer(
            Authentication auth,
            @RequestBody ServerRequest request) {
        ServerResponse response = serverService.createServer(auth.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(response, "Servidor criado com sucesso"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ServerResponse>>> getMyServers(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getMyServers(auth.getName())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ServerResponse>> getServer(
            Authentication auth,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.getServer(auth.getName(), id)));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<String>> joinServer(
            Authentication auth,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(serverService.joinServer(auth.getName(), id)));
    }

    @PostMapping("/{id}/channels")
    public ResponseEntity<ApiResponse<ChannelResponse>> createChannel(
            Authentication auth,
            @PathVariable Long id,
            @RequestBody ChannelRequest request) {
        ChannelResponse response = serverService.createChannel(auth.getName(), id, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Canal criado com sucesso"));
    }
}
