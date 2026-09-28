package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.UserResponse;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.mapper.UserMapper;
import com.chatapp.chatapp_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserResponse getProfile(String email) {
        User user = findUser(email);
        return userMapper.toResponse(user);
    }

    public UserResponse updateUsername(String email, String newUsername) {
        User user = findUser(email);

        if (userRepository.existsByUsername(newUsername)) {
            throw new RuntimeException("Username ja esta em uso");
        }

        user.setUsername(newUsername);
        userRepository.save(user);
        return userMapper.toResponse(user);
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Usuario nao encontrado"));
    }
}
