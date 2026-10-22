package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.chatapp.chatapp_backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DirectUnreadTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    String account() {
        var u=new User();u.setEmail(UUID.randomUUID()+"@test.com");u.setUsername(UUID.randomUUID().toString());u.setPassword("test-hash");
        return users.save(u).getEmail();
    }
    @Test void unreadIsPrivateAndReadOnlyThroughDisplayedMessage() throws Exception {
        String a=account(),b=account(),c=account();
        String ta="Bearer "+jwt.generateToken(a),tb="Bearer "+jwt.generateToken(b),tc="Bearer "+jwt.generateToken(c);
        var response=mvc.perform(post("/api/direct-messages").param("contact",b).header("Authorization",ta)
            .contentType("application/json").content("{\"content\":\"first\"}")).andExpect(status().isOk()).andReturn();
        long id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/direct-messages").param("contact",b).header("Authorization",ta)
            .contentType("application/json").content("{\"content\":\"second\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/direct-messages/unread").header("Authorization",tb))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].count").value(2))
            .andExpect(jsonPath("$.data[0].senderEmail").value(org.hamcrest.Matchers.startsWith("usr_")));
        mvc.perform(get("/api/direct-messages/unread").header("Authorization",ta)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/api/direct-messages/read").param("contact",a).param("throughId",String.valueOf(id)).header("Authorization",tc)).andExpect(status().isBadRequest());
        for(int i=0;i<2;i++) mvc.perform(post("/api/direct-messages/read").param("contact",a).param("throughId",String.valueOf(id)).header("Authorization",tb)).andExpect(status().isOk());
        mvc.perform(get("/api/direct-messages/unread").header("Authorization",tb)).andExpect(jsonPath("$.data[0].count").value(1));
        mvc.perform(get("/api/direct-messages/unread").header("Authorization",tc)).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(post("/api/direct-messages/read").param("contact",a).param("throughId","9223372036854775807").header("Authorization",tb)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/direct-messages/unread")).andExpect(status().isUnauthorized());
    }
}
