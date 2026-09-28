package com.chatapp.chatapp_backend.controller;

import com.chatapp.chatapp_backend.dto.ApiResponse;
import com.chatapp.chatapp_backend.dto.UserResponse;
import com.chatapp.chatapp_backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getProfile(Authentication auth) {
        return ResponseEntity.ok(ApiResponse.success(userService.getProfile(auth.getName())));
    }

    @PutMapping("/me/username")
    public ResponseEntity<ApiResponse<UserResponse>> updateUsername(
            Authentication auth,
            @RequestParam String newUsername) {
        UserResponse response = userService.updateUsername(auth.getName(), newUsername);
        return ResponseEntity.ok(ApiResponse.success(response, "Username atualizado com sucesso"));
    }
}
