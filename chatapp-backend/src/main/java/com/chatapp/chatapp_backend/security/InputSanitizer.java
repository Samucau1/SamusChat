package com.chatapp.chatapp_backend.security;

import org.springframework.stereotype.Component;

@Component
public class InputSanitizer {

    public String sanitize(String input) {
        if (input == null) {
            return null;
        }

        return input
                .replaceAll("<[^>]*>", "")
                .replaceAll("(?i)javascript:", "")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "")
                .trim();
    }

    public boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        return email.matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$");
    }

    public boolean isValidUsername(String username) {
        if (username == null) {
            return false;
        }
        return username.matches("^[a-zA-Z0-9_\\-]{3,30}$");
    }

    public boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        String lower = password.toLowerCase(java.util.Locale.ROOT);
        return password.codePointCount(0, password.length()) >= 15
            && password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72
            && password.chars().noneMatch(Character::isISOControl)
            && !password.matches("(.)\\1+")
            && !java.util.Set.of("passwordpassword", "123456789012345", "1234567890123456", "qwertyuiopasdfgh", "senha12345678901").contains(lower);
    }
}
