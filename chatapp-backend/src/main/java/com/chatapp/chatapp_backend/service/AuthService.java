package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.LoginRequest;
import com.chatapp.chatapp_backend.dto.RegisterRequest;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.chatapp.chatapp_backend.security.InputSanitizer;
import com.chatapp.chatapp_backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final InputSanitizer sanitizer;

    public String register(RegisterRequest request) {
        String username = sanitizer.sanitize(request.getUsername());
        String email = sanitizer.sanitize(request.getEmail());
        String password = request.getPassword();

        if (!sanitizer.isValidUsername(username)) {
            throw new RuntimeException("Username invalido. Use 3-30 caracteres: letras, numeros, _ ou -");
        }
        if (!sanitizer.isValidEmail(email)) {
            throw new RuntimeException("Email invalido");
        }
        if (!sanitizer.isValidPassword(password)) {
            throw new RuntimeException("Senha deve ter entre 6 e 100 caracteres");
        }

        String normalizedEmail = email.toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new RuntimeException("Email ja cadastrado");
        }
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Username ja cadastrado");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));

        userRepository.save(user);
        return jwtUtil.generateToken(user.getEmail());
    }

    public String login(LoginRequest request) {
        String email = sanitizer.sanitize(request.getEmail());

        if (!sanitizer.isValidEmail(email)) {
            throw new RuntimeException("Email invalido");
        }

        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new RuntimeException("Credenciais invalidas"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Credenciais invalidas");
        }

        return jwtUtil.generateToken(user.getEmail());
    }
}
