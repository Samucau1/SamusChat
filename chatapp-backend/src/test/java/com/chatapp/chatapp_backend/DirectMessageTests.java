package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.chatapp.chatapp_backend.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class DirectMessageTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    private String account() {
        var user = new User();
        user.setEmail(UUID.randomUUID()+"@test.com");
        user.setUsername(UUID.randomUUID().toString());
        user.setPassword("test-hash");
        return users.save(user).getEmail();
    }
    @Test void messagesArePersistedBidirectionalAndPrivate() throws Exception {
        String a = account(), b = account(), outsider = account();
        mvc.perform(post("/api/direct-messages").param("contact", b)
            .header("Authorization", "Bearer "+jwt.generateToken(a))
            .contentType("application/json").content("{\"content\":\"Olá!\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.senderEmail").value(a));
        mvc.perform(get("/api/direct-messages").param("contact", a)
            .header("Authorization", "Bearer "+jwt.generateToken(b)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].content").value("Olá!"));
        mvc.perform(get("/api/direct-messages").param("contact", a)
            .header("Authorization", "Bearer "+jwt.generateToken(outsider)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/direct-messages").param("contact", b)).andExpect(status().isUnauthorized());
    }
    @Test void invalidMessagesAndMissingContactsAreRejected() throws Exception {
        String a = account(), b = account(), token = "Bearer "+jwt.generateToken(a);
        for (String text : new String[]{"", " ", "x".repeat(2001)}) {
            mvc.perform(post("/api/direct-messages").param("contact", b).header("Authorization", token)
                .contentType("application/json").content("{\"content\":\""+text+"\"}"))
                .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/direct-messages").param("contact", "missing@test.com").header("Authorization", token))
            .andExpect(status().isBadRequest());
    }
}
