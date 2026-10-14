package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.security.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc
class SessionRefreshTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JwtUtil jwt;

    @Test void refreshIsSeparateFromAccessAndRevokedByLogout() throws Exception {
        String name = "session_" + UUID.randomUUID().toString().substring(0, 8);
        var result = mvc.perform(post("/api/auth/register").contentType("application/json")
            .content(json.writeValueAsString(Map.of("username", name, "email", name + "@example.test", "password", "Orbit!River!Copper!928"))))
            .andExpect(status().isOk()).andReturn();
        var data = json.readTree(result.getResponse().getContentAsString()).path("data");
        String access = data.path("token").asText(), refresh = data.path("refreshToken").asText();
        assertThat(refresh).isNotBlank();
        assertThat(jwt.isTokenValid(refresh)).isFalse();
        assertThat(jwt.isTokenValid(access)).isTrue();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + refresh)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content(json.writeValueAsString(Map.of("refreshToken", access))))
            .andExpect(status().isUnauthorized());
        var renewed = mvc.perform(post("/api/auth/refresh").contentType("application/json").content(json.writeValueAsString(Map.of("refreshToken", refresh))))
            .andExpect(status().isOk()).andReturn();
        String token = json.readTree(renewed.getResponse().getContentAsString()).path("data").path("token").asText();
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content(json.writeValueAsString(Map.of("refreshToken", refresh))))
            .andExpect(status().isUnauthorized());
    }

    @Test void malformedRefreshIsRejected() throws Exception {
        mvc.perform(post("/api/auth/refresh").contentType("application/json").content("{\"refreshToken\":\"invalid\"}"))
            .andExpect(status().isUnauthorized());
    }
}
