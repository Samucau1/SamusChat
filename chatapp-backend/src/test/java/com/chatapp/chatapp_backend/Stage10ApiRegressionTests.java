package com.chatapp.chatapp_backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class Stage10ApiRegressionTests {
    @Autowired private MockMvc api;
    @Autowired private ObjectMapper json;

    @Test
    void factoriesAndMappersPreserveProfileServerAndMemberContracts() throws Exception {
        String owner = register("owner", "owner@test.com");
        String member = register("member", "member@test.com");
        success(api.perform(get("/api/users/me").header("Authorization", owner)))
                .andExpect(jsonPath("$.data.email").value("owner@test.com"))
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.createdAt").isString());
        success(api.perform(put("/api/users/me/username").header("Authorization", owner)
                        .param("newUsername", "renamed")))
                .andExpect(jsonPath("$.data.username").value("renamed"));

        JsonNode server = createServer(owner);
        long serverId = server.path("id").asLong();
        assertThat(server.path("name").asText()).isEqualTo("Servidor");
        assertThat(server.path("description").asText()).isEqualTo("Descricao");
        assertThat(server.path("ownerEmail").asText()).isEqualTo("owner@test.com");
        assertThat(server.path("channels").get(0).path("name").asText()).isEqualTo("geral");
        assertThat(server.path("channels").get(0).path("type").asText()).isEqualTo("TEXT");
        success(api.perform(post("/api/servers/{id}/join", serverId).header("Authorization", member)));
        // A joined server must stay visible, even when this account is not its owner.
        success(api.perform(get("/api/servers").header("Authorization", member)))
                .andExpect(jsonPath("$.data[0].id").value(serverId));
        success(api.perform(get("/api/servers/{id}", serverId).header("Authorization", member)))
                .andExpect(jsonPath("$.data.channels[0].name").value("geral"));
        success(api.perform(get("/api/servers/{id}/members", serverId).header("Authorization", owner)))
                .andExpect(jsonPath("$.data.length()").value(2));
        success(api.perform(put("/api/servers/{id}/members/role", serverId).header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userEmail", "member@test.com", "role", "MOD")))))
                .andExpect(jsonPath("$.data.role").value("MOD"))
                .andExpect(jsonPath("$.data.username").value("member"));
        success(api.perform(delete("/api/servers/{id}/members/{email}", serverId, "member@test.com")
                .header("Authorization", owner))).andExpect(jsonPath("$.data").doesNotExist());
        success(api.perform(get("/api/servers").header("Authorization", member)))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    @Test
    void messageFactoryPreservesTrimmingPaginationAttachmentsAndDeleteEnvelope() throws Exception {
        String owner = register("owner", "owner@test.com");
        long channel = createServer(owner).path("channels").get(0).path("id").asLong();
        JsonNode first = data(success(send(owner, channel, "  primeira  "))
                .andExpect(jsonPath("$.data.content").value("primeira"))
                .andExpect(jsonPath("$.data.senderEmail").value("owner@test.com"))
                .andExpect(jsonPath("$.data.senderUsername").value("owner"))
                .andExpect(jsonPath("$.data.type").value("CHAT")));
        JsonNode second = data(success(send(owner, channel, "segunda")));
        success(api.perform(get("/api/channels/{id}/messages", channel).header("Authorization", owner)
                        .param("page", "0").param("size", "1")))
                .andExpect(jsonPath("$.data[0].id").value(second.path("id").asLong()));
        success(api.perform(get("/api/channels/{id}/messages", channel).header("Authorization", owner)
                        .param("page", "1").param("size", "1")))
                .andExpect(jsonPath("$.data[0].id").value(first.path("id").asLong()));
        success(api.perform(get("/api/channels/{id}/messages", channel).header("Authorization", owner)))
                .andExpect(jsonPath("$.data[0].content").value("primeira"))
                .andExpect(jsonPath("$.data[1].content").value("segunda"));

        var file = new MockMultipartFile("file", "note.txt", "text/plain", "anexo".getBytes());
        JsonNode attachment = data(success(api.perform(multipart("/api/channels/{id}/upload", channel)
                        .file(file).header("Authorization", owner)))
                .andExpect(jsonPath("$.data.attachmentType").value("FILE"))
                .andExpect(jsonPath("$.data.attachmentUrl").isString())
                .andExpect(jsonPath("$.data.content").value("")));
        success(api.perform(delete("/api/channels/{channel}/messages/{id}", channel, first.path("id").asLong())
                .header("Authorization", owner))).andExpect(jsonPath("$.data").doesNotExist());
        success(api.perform(get("/api/channels/{id}/messages", channel).header("Authorization", owner)))
                .andExpect(jsonPath("$.data.length()").value(2));
        success(api.perform(delete("/api/files").header("Authorization", owner)
                .param("fileUrl", attachment.path("attachmentUrl").asText())));
    }

    @Test
    void invalidMessagesAndNonMembersCannotChangeTheChannel() throws Exception {
        String owner = register("owner", "owner@test.com");
        String outsider = register("outsider", "outsider@test.com");
        JsonNode server = createServer(owner);
        long channel = server.path("channels").get(0).path("id").asLong();
        failure(send(owner, channel, "   "));
        failure(send(owner, channel, "x".repeat(2001)));
        failure(send(outsider, channel, "sem permissao"));
        failure(api.perform(get("/api/channels/{id}/messages", channel).header("Authorization", outsider)));
        failure(api.perform(get("/api/channels/{id}/messages", channel).header("Authorization", owner).param("page", "-1")));
        failure(api.perform(get("/api/channels/{id}/messages", channel).header("Authorization", owner).param("size", "101")));
        failure(api.perform(post("/api/servers/{id}/channels", server.path("id").asLong())
                .header("Authorization", outsider).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"canal\"}")));
        success(api.perform(get("/api/channels/{id}/messages", channel).header("Authorization", owner)))
                .andExpect(jsonPath("$.data").isEmpty());
    }

    private String register(String username, String email) throws Exception {
        JsonNode result = data(success(api.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "email", email, "password", "123456"))))));
        assertThat(result.path("token").asText()).isNotBlank();
        return "Bearer " + result.path("token").asText();
    }

    private JsonNode createServer(String token) throws Exception {
        return data(success(api.perform(post("/api/servers").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"  Servidor  \",\"description\":\"  Descricao  \"}"))));
    }

    private ResultActions send(String token, long channel, String content) throws Exception {
        return api.perform(post("/api/channels/{id}/messages", channel).header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("content", content))));
    }

    private ResultActions success(ResultActions result) throws Exception {
        return result.andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.success").value(true)).andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    private void failure(ResultActions result) throws Exception {
        result.andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.timestamp").isString()).andExpect(jsonPath("$.error").isString())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }
}
