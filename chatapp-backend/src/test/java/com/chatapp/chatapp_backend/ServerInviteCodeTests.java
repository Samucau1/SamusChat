package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.repository.ServerRepository;
import com.chatapp.chatapp_backend.repository.ServerMemberRepository;
import com.chatapp.chatapp_backend.service.ServerInviteCodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ServerInviteCodeTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ServerRepository servers;
    @Autowired ServerMemberRepository members;
    @Autowired com.chatapp.chatapp_backend.service.ServerService service;

    private record Account(String email, String token) {}
    private Account account() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        var result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 16), "email", email, "password", "IntegrationTest123!"))))
                .andExpect(status().isOk()).andReturn();
        return new Account(email, json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText());
    }
    private JsonNode create(Account owner) throws Exception {
        var result = mvc.perform(post("/api/servers").header("Authorization", "Bearer " + owner.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Preview server\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
    @Test void codeIsUniqueAndOnlyAdminsReceiveIt() throws Exception {
        var owner = account(); var member = account(); var outsider = account();
        JsonNode server = create(owner), second = create(owner);
        long id = server.path("id").asLong(); String code = server.path("inviteCode").asText();
        assertThat(ServerInviteCodes.valid(code)).isTrue();
        assertThat(second.path("inviteCode").asText()).isNotEqualTo(code);
        mvc.perform(post("/api/servers/join").header("Authorization", "Bearer " + member.token())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("code", code))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/servers/{id}", id).header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inviteCode").doesNotExist())
                .andExpect(jsonPath("$.data.myRole").value("USER"));
        mvc.perform(get("/api/servers").header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].inviteCode").doesNotExist());
        mvc.perform(get("/api/servers/{id}", id).header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/servers/{id}/channels", id).header("Authorization", "Bearer " + member.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"blocked\"}"))
                .andExpect(status().isForbidden());
        var membership = members.findByServerIdAndUserEmail(id, member.email()).orElseThrow();
        membership.setRole("MOD"); members.save(membership);
        mvc.perform(get("/api/servers/{id}", id).header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inviteCode").doesNotExist());
        membership.setRole("ADMIN"); members.save(membership);
        mvc.perform(get("/api/servers/{id}", id).header("Authorization", "Bearer " + member.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.inviteCode").value(code));
    }
    @Test void existingServerGetsPersistentCodeWithoutChangingItsIdOrChannels() throws Exception {
        var owner = account(); var created = create(owner); long id = created.path("id").asLong();
        var entity = servers.findById(id).orElseThrow(); entity.setInviteCode(null); servers.save(entity);
        var result = mvc.perform(get("/api/servers/{id}", id).header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id))
                .andExpect(jsonPath("$.data.channels[0].id").value(created.path("channels").get(0).path("id").asLong()))
                .andReturn();
        String code = json.readTree(result.getResponse().getContentAsString()).path("data").path("inviteCode").asText();
        assertThat(ServerInviteCodes.valid(code)).isTrue();
        assertThat(servers.findById(id).orElseThrow().getInviteCode()).isEqualTo(code);
    }
    @Test void specialCharactersWorkInJsonAndRequireAuthentication() throws Exception {
        var owner = account(); var member = account(); var created = create(owner); long id = created.path("id").asLong();
        var entity = servers.findById(id).orElseThrow(); entity.setInviteCode("A#%"); servers.save(entity);
        mvc.perform(post("/api/servers/join").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"A#%\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/servers/join").header("Authorization", "Bearer " + member.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"A#%\"}"))
                .andExpect(status().isOk());
    }

    @Test void installedClientsCanStillJoinByNumericIdWithoutReceivingAdminPrivileges() throws Exception {
        var owner = account(); var legacy = account(); var created = create(owner); long id = created.path("id").asLong();
        mvc.perform(post("/api/servers/{id}/join", id)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/servers/{id}/join", id).header("Authorization", "Bearer " + legacy.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Entrou no servidor: Preview server"));
        mvc.perform(get("/api/servers/{id}", id).header("Authorization", "Bearer " + legacy.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.myRole").value("USER"))
                .andExpect(jsonPath("$.data.inviteCode").doesNotExist())
                .andExpect(jsonPath("$.data.channels[0].id").value(created.path("channels").get(0).path("id").asLong()));
        mvc.perform(post("/api/servers/{id}/join", id).header("Authorization", "Bearer " + legacy.token()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/servers/{id}/channels", id).header("Authorization", "Bearer " + legacy.token())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"blocked\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void concurrentCreationDoesNotReuseCodes() throws Exception {
        var owner = account();
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<java.util.concurrent.Callable<String>>();
            for (int i = 0; i < 8; i++) tasks.add(() -> {
                var request = new com.chatapp.chatapp_backend.dto.ServerRequest();
                request.setName("Concurrent server");
                return service.createServer(owner.email(), request).getInviteCode();
            });
            var codes = new java.util.HashSet<String>();
            for (var result : executor.invokeAll(tasks)) {
                String code = result.get(15, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(ServerInviteCodes.valid(code)).isTrue();
                assertThat(codes.add(code)).isTrue();
            }
        }
    }
}
