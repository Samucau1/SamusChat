package com.chatapp.chatapp_backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class JwtUtilTest {
    private JwtUtil jwt;

    @BeforeEach
    void setup() {
        jwt = new JwtUtil();
        ReflectionTestUtils.setField(jwt, "secret", "chave_exclusiva_dos_testes_jwt_minimo_32_chars");
        ReflectionTestUtils.setField(jwt, "expiration", 86400000L);
    }

    @Test
    void generatedTokenContainsTheAuthenticatedEmail() {
        String token = jwt.generateToken("samus@test.com");
        assertThat(token).isNotBlank();
        assertThat(jwt.isTokenValid(token)).isTrue();
        assertThat(jwt.extractEmail(token)).isEqualTo("samus@test.com");
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = jwt.generateToken("samus@test.com");
        int signature = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signature) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, signature) + replacement + token.substring(signature + 1);
        assertThat(jwt.isTokenValid(tampered)).isFalse();
        assertThatThrownBy(() -> jwt.extractEmail(tampered)).isInstanceOf(io.jsonwebtoken.JwtException.class);
    }

    @Test
    void expiredTokenIsRejectedWithoutSleeping() {
        ReflectionTestUtils.setField(jwt, "expiration", -60000L);
        assertThat(jwt.isTokenValid(jwt.generateToken("samus@test.com"))).isFalse();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        String token = jwt.generateToken("samus@test.com");
        ReflectionTestUtils.setField(jwt, "secret", "outra_chave_exclusiva_de_testes_minimo_32_chars");
        assertThat(jwt.isTokenValid(token)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"invalid", "token.invalido.aqui", " "})
    void malformedTokensAreRejected(String token) {
        assertThat(jwt.isTokenValid(token)).isFalse();
    }
}
