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
        return password.length() >= 6 && password.length() <= 100;
    }
}
