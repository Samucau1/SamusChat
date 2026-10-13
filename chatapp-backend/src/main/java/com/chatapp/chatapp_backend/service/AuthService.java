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
    private final com.chatapp.chatapp_backend.security.AccountLoginLimiter loginLimiter;

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
            throw new RuntimeException("Use senha de 15 caracteres a 72 bytes, sem sequencias comuns");
        }

        String normalizedEmail = email.toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new RuntimeException("Cadastro indisponivel. Confira os dados ou recupere sua conta.");
        }
        if (userRepository.existsByUsername(username)) {
            throw new RuntimeException("Cadastro indisponivel. Confira os dados ou recupere sua conta.");
        }

        User user = new User();
        user.setUsername(username);
        user.setEmail(normalizedEmail);
        user.setPassword(passwordEncoder.encode(password));

        userRepository.save(user);
        return jwtUtil.generateToken(user.getEmail());
    }

    @org.springframework.transaction.annotation.Transactional
    public String login(LoginRequest request) {
        String email = sanitizer.sanitize(request.getEmail());

        if (!sanitizer.isValidEmail(email)) {
            throw new RuntimeException("Email invalido");
        }

        loginLimiter.attempt(email.toLowerCase(java.util.Locale.ROOT));
        if (request.getPassword() == null || request.getPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new RuntimeException("Credenciais invalidas");
        User user = userRepository.lockedByEmail(email.toLowerCase())
                .orElseThrow(() -> new RuntimeException("Credenciais invalidas"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Credenciais invalidas");
        }

        loginLimiter.success(email.toLowerCase(java.util.Locale.ROOT));
        return jwtUtil.generateToken(user.getEmail());
    }

    @org.springframework.transaction.annotation.Transactional
    public void logout(String email) {
        var user = userRepository.lockedByEmail(email).orElseThrow(() -> new SecurityException("Conta invalida"));
        user.setAuthVersion(user.getAuthVersion() + 1); userRepository.save(user);
    }
}
