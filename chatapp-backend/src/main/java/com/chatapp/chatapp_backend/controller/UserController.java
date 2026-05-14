package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.UserResponse;
import com.chatapp.chatapp_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // GET /api/users/me — retorna o perfil do usuário logado
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getProfile(Authentication auth) {
        // Authentication.getName() retorna o email que registramos no JwtFilter
        return ResponseEntity.ok(userService.getProfile(auth.getName()));
    }

    // PUT /api/users/me/username — atualiza o username
    @PutMapping("/me/username")
    public ResponseEntity<UserResponse> updateUsername(
            Authentication auth,
            @RequestParam String newUsername) {
        return ResponseEntity.ok(userService.updateUsername(auth.getName(), newUsername));
    }
}