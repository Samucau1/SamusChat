package com.chatapp.chatapp_backend;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatapp.chatapp_backend.repository.ChannelRepository;
import com.chatapp.chatapp_backend.repository.MessageRepository;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import com.chatapp.chatapp_backend.repository.ServerRepository;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class FileControllerTests {

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
    void cleanDatabase() throws Exception {
        messageRepository.deleteAll();
        channelRepository.deleteAll();
        serverMemberRepository.deleteAll();
        serverRepository.deleteAll();
        userRepository.deleteAll();

        Path uploadDir = Path.of("target/test-uploads");
        if (Files.exists(uploadDir)) {
            try (var files = Files.list(uploadDir)) {
                files.forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    @Test
    void uploadImageCreatesAttachmentMessageAndExposesPublicFile() throws Exception {
        String token = registerAndLogin();
        long channelId = createServerAndGetGeneralChannelId(token);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "samus.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[] {1, 2, 3, 4}
        );

        MvcResult uploadResult = mockMvc.perform(multipart("/api/channels/{channelId}/upload", channelId)
                        .file(file)
                        .param("content", "Olha essa imagem!")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("Olha essa imagem!"))
                .andExpect(jsonPath("$.data.attachmentUrl").value(startsWith("http://localhost:8080/uploads/")))
                .andExpect(jsonPath("$.data.attachmentUrl").value(endsWith(".png")))
                .andExpect(jsonPath("$.data.attachmentType").value("IMAGE"))
                .andReturn();

        String attachmentUrl = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).get("data")
                .get("attachmentUrl")
                .asText();
        String fileName = attachmentUrl.substring(attachmentUrl.lastIndexOf("/") + 1);

        mockMvc.perform(get("/uploads/{fileName}", fileName))
                .andExpect(status().isOk())
                .andExpect(content().bytes(new byte[] {1, 2, 3, 4}));

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attachmentUrl").value(attachmentUrl))
                .andExpect(jsonPath("$.data[0].attachmentType").value("IMAGE"));
    }

    @Test
    void uploadTextWithoutCaptionIsReadableAndPresentInHistory() throws Exception {
        String token = registerAndLogin();
        long channelId = createServerAndGetGeneralChannelId(token);
        byte[] bytes = "Anexo de teste".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "teste.txt", "text/plain", bytes);
        MvcResult result = mockMvc.perform(multipart("/api/channels/{channelId}/upload", channelId)
                        .file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachmentType").value("FILE"))
                .andReturn();
        String url = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("data").get("attachmentUrl").asText();
        mockMvc.perform(get(java.net.URI.create(url).getPath()))
                .andExpect(status().isOk()).andExpect(content().bytes(bytes));
        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].attachmentUrl").value(url));
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
                .andReturn();

        return objectMapper.readTree(createServerResult.getResponse().getContentAsString()).get("data")
                .get("channels")
                .get(0)
                .get("id")
                .asLong();
    }
}
