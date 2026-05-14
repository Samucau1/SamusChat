package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.UserResponse;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    // Busca o perfil pelo email extraído do token JWT
    public UserResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));
        return toResponse(user);
    }

    // Atualiza o username do usuário
    public UserResponse updateUsername(String email, String newUsername) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        if (userRepository.existsByUsername(newUsername)) {
            throw new RuntimeException("Username já está em uso");
        }

        user.setUsername(newUsername);
        userRepository.save(user);
        return toResponse(user);
    }

    // Converte User (entidade) para UserResponse (DTO)
    // Isso evita expor a senha na resposta
    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }
}

