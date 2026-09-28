package com.chatapp.chatapp_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatapp.chatapp_backend.entity.UserDevice;
import com.chatapp.chatapp_backend.repository.UserDeviceRepository;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DeviceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserDeviceRepository userDeviceRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanDatabase() {
        userDeviceRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void deviceFlowRegistersUpdatesAndUnregistersFcmToken() throws Exception {
        String token = registerAndLogin();

        mockMvc.perform(post("/api/devices/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fcmToken": "fcm-token-123",
                                  "deviceModel": "Samsung Galaxy S24"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Dispositivo registrado com sucesso"));

        UserDevice device = userDeviceRepository.findByFcmToken("fcm-token-123").orElseThrow();
        assertThat(device.getUserEmail()).isEqualTo("samus@test.com");
        assertThat(device.getDeviceModel()).isEqualTo("Samsung Galaxy S24");

        mockMvc.perform(post("/api/devices/register")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fcmToken": "fcm-token-123",
                                  "deviceModel": "Pixel 9"
                                }
                                """))
                .andExpect(status().isOk());

        assertThat(userDeviceRepository.findAll()).hasSize(1);
        assertThat(userDeviceRepository.findByFcmToken("fcm-token-123").orElseThrow().getDeviceModel())
                .isEqualTo("Pixel 9");

        mockMvc.perform(delete("/api/devices/unregister")
                        .header("Authorization", "Bearer " + token)
                        .param("fcmToken", "fcm-token-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Dispositivo removido com sucesso"));

        assertThat(userDeviceRepository.existsByFcmToken("fcm-token-123")).isFalse();
    }

    private String registerAndLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .header("X-Forwarded-For", "10.0.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "samus",
                                  "email": "samus@test.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "10.0.1.1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "samus@test.com",
                                  "password": "123456"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("data");
        return loginBody.get("token").asText();
    }
}
