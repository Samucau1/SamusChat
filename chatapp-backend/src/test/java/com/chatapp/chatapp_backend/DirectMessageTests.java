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
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.chatapp.chatapp_backend.controller.DirectMessageController controller;
    @org.springframework.boot.test.mock.mockito.SpyBean com.chatapp.chatapp_backend.websocket.DirectMessageBroadcaster broadcaster;

    @Test void rollbackNeverDeliversAndCommitDeliversOnceToEachParticipant() {
        String a = account(), b = account();
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(a, null);
        var request = new com.chatapp.chatapp_backend.dto.MessageRequest(); request.setContent("transaction test");
        org.mockito.Mockito.clearInvocations(broadcaster);
        new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status -> {
            controller.send(auth, b, request);
            org.mockito.Mockito.verify(broadcaster, org.mockito.Mockito.never()).deliver(org.mockito.ArgumentMatchers.any());
            status.setRollbackOnly(); return null;
        });
        org.mockito.Mockito.verify(broadcaster, org.mockito.Mockito.never()).deliver(org.mockito.ArgumentMatchers.any());
        controller.send(auth, b, request);
        org.mockito.Mockito.verify(broadcaster, org.mockito.Mockito.times(1)).deliver(org.mockito.ArgumentMatchers.any());
    }
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
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.senderEmail").value(org.hamcrest.Matchers.startsWith("usr_")));
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
