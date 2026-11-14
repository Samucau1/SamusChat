package com.chatapp.chatapp_backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ServerControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void serverFlowCreatesListsAndAddsChannel() throws Exception {
        String token = registerAndLogin();

        MvcResult createServerResult = mockMvc.perform(post("/api/servers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Servidor da Samus",
                                  "description": "Meu primeiro servidor"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("Servidor da Samus"))
                .andExpect(jsonPath("$.data.channels[0].name").value("geral"))
                .andReturn();

        JsonNode createdServer = objectMapper.readTree(createServerResult.getResponse().getContentAsString()).get("data");
        long serverId = createdServer.get("id").asLong();
        long generalChannelId = createdServer.get("channels").get(0).get("id").asLong();
        assertThat(serverId).isPositive();
        assertThat(generalChannelId).isPositive();

        mockMvc.perform(get("/api/servers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(serverId))
                .andExpect(jsonPath("$.data[0].name").value("Servidor da Samus"));

        MvcResult createChannelResult = mockMvc.perform(post("/api/servers/{id}/channels", serverId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "jogos",
                                  "type": "TEXT"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("jogos"))
                .andExpect(jsonPath("$.data.type").value("TEXT"))
                .andReturn();

        long gamesChannelId = objectMapper.readTree(createChannelResult.getResponse().getContentAsString())
                .get("data").get("id").asLong();
        assertThat(gamesChannelId).isPositive().isNotEqualTo(generalChannelId);

        mockMvc.perform(get("/api/servers/{id}", serverId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(serverId))
                .andExpect(jsonPath("$.data.channels.length()").value(2))
                .andExpect(jsonPath("$.data.channels[0].id").value(generalChannelId))
                .andExpect(jsonPath("$.data.channels[0].name").value("geral"))
                .andExpect(jsonPath("$.data.channels[1].id").value(gamesChannelId))
                .andExpect(jsonPath("$.data.channels[1].name").value("jogos"));
    }

    private String registerAndLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "samus",
                                  "email": "samus@test.com",
                                  "password": "IntegrationTest123!"
                                }
                                """))
                .andExpect(status().isOk());

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "samus@test.com",
                                  "password": "IntegrationTest123!"
                                }
                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andReturn();

        JsonNode loginBody = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return loginBody.get("data").get("token").asText();
    }
}
