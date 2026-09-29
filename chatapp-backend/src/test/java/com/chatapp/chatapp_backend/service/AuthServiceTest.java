package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.dto.LoginRequest;
import com.chatapp.chatapp_backend.dto.RegisterRequest;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.chatapp.chatapp_backend.security.InputSanitizer;
import com.chatapp.chatapp_backend.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock private UserRepository users;
    @Mock private PasswordEncoder passwords;
    @Mock private JwtUtil jwt;
    @Mock private InputSanitizer sanitizer;
    private AuthService service;

    @BeforeEach
    void setup() {
        service = new AuthService(users, passwords, jwt, sanitizer);
    }

    @Test
    void registerNormalizesEmailAndPersistsOnlyTheEncodedPassword() {
        RegisterRequest request = registration();
        request.setEmail("SAMUS@test.com");
        validRegistration(request);
        when(passwords.encode("123456")).thenReturn("bcrypt-hash");
        when(jwt.generateToken("samus@test.com")).thenReturn("signed-token");

        assertThat(service.register(request)).isEqualTo("signed-token");

        var saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("samus@test.com");
        assertThat(saved.getValue().getUsername()).isEqualTo("samus");
        assertThat(saved.getValue().getPassword()).isEqualTo("bcrypt-hash").isNotEqualTo(request.getPassword());
        verify(users).existsByEmail("samus@test.com");
    }

    @Test
    void duplicateEmailDoesNotSaveOrGenerateToken() {
        RegisterRequest request = registration();
        validRegistration(request);
        when(users.existsByEmail(request.getEmail())).thenReturn(true);
        assertThatThrownBy(() -> service.register(request)).hasMessage("Email ja cadastrado");
        verify(users, never()).save(any());
        verifyNoInteractions(passwords, jwt);
    }

    @Test
    void duplicateUsernameDoesNotSaveOrGenerateToken() {
        RegisterRequest request = registration();
        validRegistration(request);
        when(users.existsByUsername(request.getUsername())).thenReturn(true);
        assertThatThrownBy(() -> service.register(request)).hasMessage("Username ja cadastrado");
        verify(users, never()).save(any());
        verifyNoInteractions(passwords, jwt);
    }

    @ParameterizedTest
    @CsvSource({"username,Username invalido", "email,Email invalido", "password,Senha deve ter"})
    void invalidRegistrationStopsBeforePersistence(String invalidField, String error) {
        RegisterRequest request = registration();
        when(sanitizer.sanitize(request.getUsername())).thenReturn(request.getUsername());
        when(sanitizer.sanitize(request.getEmail())).thenReturn(request.getEmail());
        when(sanitizer.isValidUsername(request.getUsername())).thenReturn(!invalidField.equals("username"));
        if (!invalidField.equals("username")) {
            when(sanitizer.isValidEmail(request.getEmail())).thenReturn(!invalidField.equals("email"));
        }
        // An unstubbed password validation returns false.
        assertThatThrownBy(() -> service.register(request)).hasMessageContaining(error);
        verifyNoInteractions(users, passwords, jwt);
    }

    @Test
    void loginNormalizesEmailAndReturnsTokenForTheStoredAccount() {
        LoginRequest request = login("123456");
        request.setEmail("SAMUS@test.com");
        validLogin(request);
        when(users.findByEmail("samus@test.com")).thenReturn(Optional.of(user()));
        when(passwords.matches("123456", "bcrypt-hash")).thenReturn(true);
        when(jwt.generateToken("samus@test.com")).thenReturn("signed-token");
        assertThat(service.login(request)).isEqualTo("signed-token");
        verify(users, never()).save(any());
    }

    @Test
    void wrongPasswordReturnsGenericErrorAndDoesNotIssueToken() {
        LoginRequest request = login("wrong-password");
        validLogin(request);
        when(users.findByEmail(request.getEmail())).thenReturn(Optional.of(user()));
        assertThatThrownBy(() -> service.login(request)).hasMessage("Credenciais invalidas");
        verify(passwords).matches("wrong-password", "bcrypt-hash");
        verifyNoInteractions(jwt);
    }

    @Test
    void unknownEmailReturnsTheSameGenericError() {
        LoginRequest request = login("123456");
        validLogin(request);
        when(users.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(request)).hasMessage("Credenciais invalidas");
        verifyNoInteractions(passwords, jwt);
    }

    @Test
    void invalidLoginEmailDoesNotQueryTheDatabase() {
        LoginRequest request = login("123456");
        when(sanitizer.sanitize(request.getEmail())).thenReturn("invalid");
        assertThatThrownBy(() -> service.login(request)).hasMessage("Email invalido");
        verifyNoInteractions(users, passwords, jwt);
    }

    private void validRegistration(RegisterRequest request) {
        when(sanitizer.sanitize(request.getUsername())).thenReturn(request.getUsername());
        when(sanitizer.sanitize(request.getEmail())).thenReturn(request.getEmail());
        when(sanitizer.isValidUsername(request.getUsername())).thenReturn(true);
        when(sanitizer.isValidEmail(request.getEmail())).thenReturn(true);
        when(sanitizer.isValidPassword(request.getPassword())).thenReturn(true);
    }

    private void validLogin(LoginRequest request) {
        when(sanitizer.sanitize(request.getEmail())).thenReturn(request.getEmail());
        when(sanitizer.isValidEmail(request.getEmail())).thenReturn(true);
    }

    private RegisterRequest registration() {
        var request = new RegisterRequest();
        request.setUsername("samus"); request.setEmail("samus@test.com"); request.setPassword("123456");
        return request;
    }

    private LoginRequest login(String password) {
        var request = new LoginRequest();
        request.setEmail("samus@test.com"); request.setPassword(password);
        return request;
    }

    private User user() {
        var user = new User();
        user.setEmail("samus@test.com"); user.setPassword("bcrypt-hash");
        return user;
    }
}
