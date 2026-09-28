package com.chatapp.chatapp_backend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.repository.MessageRepository;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import com.chatapp.chatapp_backend.repository.ServerRepository;
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
class MessageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageRepository messageRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Autowired
    private ServerMemberRepository serverMemberRepository;

    @Autowired
    private ServerRepository serverRepository;

    @Autowired
    private UserRepository userRepository;

    @AfterEach
    void cleanDatabase() {
        messageRepository.deleteAll();
        channelRepository.deleteAll();
        serverMemberRepository.deleteAll();
        serverRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void messageFlowSendsListsPaginatesAndDeletes() throws Exception {
        String token = registerAndLogin();
        long channelId = createServerAndGetGeneralChannelId(token);

        MvcResult firstMessageResult = mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Ol\u00e1 pessoal!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.content").value("Ol\u00e1 pessoal!"))
                .andExpect(jsonPath("$.data.senderUsername").value("samus"))
                .andExpect(jsonPath("$.data.senderEmail").value("samus@test.com"))
                .andExpect(jsonPath("$.data.channelId").value(channelId))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andReturn();

        long firstMessageId = objectMapper.readTree(firstMessageResult.getResponse().getContentAsString()).get("data")
                .get("id")
                .asLong();

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Segunda mensagem!"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(2))
                .andExpect(jsonPath("$.data.content").value("Segunda mensagem!"))
                .andExpect(jsonPath("$.data.senderUsername").value("samus"));

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].content").value("Ol\u00e1 pessoal!"))
                .andExpect(jsonPath("$.data[1].id").value(2))
                .andExpect(jsonPath("$.data[1].content").value("Segunda mensagem!"));

        mockMvc.perform(get("/api/channels/{channelId}/messages?page=0&size=10", channelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].content").value("Ol\u00e1 pessoal!"))
                .andExpect(jsonPath("$.data[1].content").value("Segunda mensagem!"));

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}", channelId, firstMessageId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String registerAndLogin() throws Exception {
        mockMvc.perform(post("/api/auth/register")
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

    private long createServerAndGetGeneralChannelId(String token) throws Exception {
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
                .andExpect(jsonPath("$.data.channels[0].id").value(1))
                .andExpect(jsonPath("$.data.channels[0].name").value("geral"))
                .andReturn();

        return objectMapper.readTree(createServerResult.getResponse().getContentAsString()).get("data")
                .get("channels")
                .get(0)
                .get("id")
                .asLong();
    }
}
