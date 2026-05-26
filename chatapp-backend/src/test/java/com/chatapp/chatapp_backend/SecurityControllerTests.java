package com.chatapp.chatapp_backend;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatapp.chatapp_backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SecurityControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanDatabase() {
        userRepository.deleteAll();
    }

    @Test
    void authRouteReturnsTooManyRequestsAfterFiveAttemptsPerMinute() throws Exception {
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/auth/login")
                            .header("X-Forwarded-For", "10.0.0.8")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "email": "teste@test.com",
                                      "password": "errada"
                                    }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("Credenciais invalidas"));
        }

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.0.8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "teste@test.com",
                                  "password": "errada"
                                }
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("X-Rate-Limit-Retry-After"))
                .andExpect(jsonPath("$.error").value(containsString("Muitas requisicoes")));
    }

    @Test
    void protectedRouteWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("X-Forwarded-For", "10.0.0.9"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Nao autorizado"));
    }

    @Test
    void invalidTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("X-Forwarded-For", "10.0.0.10")
                        .header("Authorization", "Bearer tokeninvalido123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Nao autorizado"));
    }

    @Test
    void registerRejectsHtmlUsernameAfterSanitization() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Forwarded-For", "10.0.0.11")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "<script>alert(1)</script>",
                                  "email": "xss@test.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Username invalido")));
    }

    @Test
    void errorResponseDoesNotExposeStackTrace() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.0.12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "naoexiste@test.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Credenciais invalidas"))
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(content().string(not(containsString("java.lang"))));
    }
}
