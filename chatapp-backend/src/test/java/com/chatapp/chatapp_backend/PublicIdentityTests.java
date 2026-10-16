package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.chatapp.chatapp_backend.security.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class PublicIdentityTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    @Autowired PublicIdentityService identities;
    @Autowired ObjectMapper mapper;
    private User account() {
        var user = new User(); user.setEmail(UUID.randomUUID()+"@private.test");
        user.setUsername("name_"+UUID.randomUUID()); user.setPassword("unused-hash"); return users.save(user);
    }

    @Test void authenticationObjectsNeverRenderPasswordsOrTokensInLogs() {
        var login=new com.chatapp.chatapp_backend.dto.LoginRequest(); login.setPassword("synthetic-secret");
        var register=new com.chatapp.chatapp_backend.dto.RegisterRequest(); register.setPassword("synthetic-secret");
        assertThat(login.toString()).doesNotContain("synthetic-secret");
        assertThat(register.toString()).doesNotContain("synthetic-secret");
        assertThat(new com.chatapp.chatapp_backend.controller.AuthController.RefreshRequest("synthetic-secret").toString()).doesNotContain("synthetic-secret");
        assertThat(new com.chatapp.chatapp_backend.controller.GoogleAuthController.GoogleRequest("synthetic-secret","synthetic-secret").toString()).doesNotContain("synthetic-secret");
        assertThat(new com.chatapp.chatapp_backend.controller.GoogleAuthController.GisRequest("synthetic-secret","synthetic-secret","synthetic-secret").toString()).doesNotContain("synthetic-secret");
        assertThat(new com.chatapp.chatapp_backend.controller.RecoveryController.ResetRequest("user@test.com","synthetic-secret","synthetic-secret").toString()).doesNotContain("synthetic-secret");
        assertThat(new com.chatapp.chatapp_backend.controller.RecoveryController.VerifyRequest("user@test.com","synthetic-secret").toString()).doesNotContain("synthetic-secret");
    }

    @Test void contactsExposePublicIdentifiersWhileOwnProfileKeepsOwnEmail() throws Exception {
        var a=account(); var b=account(); String token="Bearer "+jwt.generateToken(a.getEmail());
        String response=mvc.perform(get("/api/calls/contacts").header("Authorization",token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(response).contains(identities.publicId(b.getEmail())).doesNotContain(a.getEmail(),b.getEmail());
        mvc.perform(get("/api/users/me").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(a.getEmail()))
                .andExpect(jsonPath("$.data.publicId").value(identities.publicId(a.getEmail())));
    }

    @Test void directHistoryAndCallSignalingNeverDiscloseOtherEmails() throws Exception {
        var a=account(); var b=account(); String token="Bearer "+jwt.generateToken(a.getEmail());
        String id=identities.publicId(b.getEmail());
        String message=mvc.perform(post("/api/direct-messages").param("contact",id).header("Authorization",token)
                .contentType("application/json").content("{\"content\":\"hello\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(message).contains(identities.publicId(a.getEmail()),id).doesNotContain(a.getEmail(),b.getEmail());
        String history=mvc.perform(get("/api/direct-messages").param("contact",identities.publicId(a.getEmail()))
                .header("Authorization","Bearer "+jwt.generateToken(b.getEmail())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(history).contains("hello").doesNotContain(a.getEmail(),b.getEmail());
        String call=mvc.perform(post("/api/calls").header("Authorization",token).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("callee",id,"requestId",UUID.randomUUID().toString()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(call).doesNotContain(a.getEmail(),b.getEmail()).contains(id);
    }

    @Test void socketFramesAreSanitizedAndForgedPublicIdsAreRejected() throws Exception {
        var a=account(); var b=account();
        var body=mapper.writeValueAsBytes(Map.of("senderEmail",a.getEmail(),"recipientEmail",b.getEmail(),"content","untouched text"));
        String frame=new String(identities.externalFrame(body),StandardCharsets.UTF_8);
        assertThat(frame).doesNotContain(a.getEmail(),b.getEmail()).contains("untouched text",identities.publicId(a.getEmail()));
        assertThat(identities.resolve(identities.publicId(a.getEmail()))).isEqualTo(a.getEmail());
        assertThatThrownBy(()->identities.resolve(identities.publicId(a.getEmail())+"tampered")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void serversAndMembershipsUsePublicIdentityAndStillAuthorizeRoleUpdates() throws Exception {
        var a=account(); var b=account(); String owner="Bearer "+jwt.generateToken(a.getEmail());
        String server=mvc.perform(post("/api/servers").header("Authorization",owner).contentType("application/json")
                .content("{\"name\":\"privacy\",\"description\":\"test\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(server).doesNotContain(a.getEmail());
        long id=mapper.readTree(server).path("data").path("id").asLong();
        mvc.perform(post("/api/servers/"+id+"/join").header("Authorization","Bearer "+jwt.generateToken(b.getEmail())))
                .andExpect(status().isOk());
        String members=mvc.perform(get("/api/servers/"+id+"/members").header("Authorization",owner))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(members).doesNotContain(a.getEmail(),b.getEmail()).contains(identities.publicId(b.getEmail()));
        mvc.perform(put("/api/servers/"+id+"/members/role").header("Authorization",owner).contentType("application/json")
                .content(mapper.writeValueAsString(Map.of("userEmail",identities.publicId(b.getEmail()),"role","MOD"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("MOD"));
    }
}
