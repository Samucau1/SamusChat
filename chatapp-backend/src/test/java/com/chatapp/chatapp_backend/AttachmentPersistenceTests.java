package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.dto.ServerRequest;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.*;
import com.chatapp.chatapp_backend.security.JwtUtil;
import com.chatapp.chatapp_backend.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "file.upload-dir=target/persistent-attachment-tests")
@AutoConfigureMockMvc
class AttachmentPersistenceTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtUtil jwt;
    @Autowired ServerService servers;
    @Autowired AttachmentBlobRepository blobs;
    @Autowired MessageRepository messages;
    @Autowired ChannelRepository channels;
    @Autowired AttachmentService attachments;
    @Autowired TransactionTemplate transactions;
    private User account() {
        var user = new User(); user.setUsername("u" + UUID.randomUUID().toString().replace("-", ""));
        user.setEmail(UUID.randomUUID() + "@example.test"); user.setPassword("test-fixture-only");
        return users.save(user);
    }
    private long channel(User owner) {
        var request = new ServerRequest(); request.setName("Persistent attachments");
        return servers.createServer(owner.getEmail(), request).getChannels().getFirst().getId();
    }
    private String token(User user) { return "Bearer " + jwt.generateToken(user.getEmail()); }
    private MockMultipartFile file() { return new MockMultipartFile("file", "example.txt", "text/plain", "Preserved bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    @Test void newUploadSurvivesWithoutFilesystemAndRetainsAuthorizationAndDeletion() throws Exception {
        var owner = account(); var outsider = account(); long channel = channel(owner);
        var result = mvc.perform(multipart("/api/channels/{id}/upload", channel).file(file()).param("content", "Keep this message").header("Authorization", token(owner)))
                .andExpect(status().isOk()).andReturn();
        var data = json.readTree(result.getResponse().getContentAsString()).path("data");
        String url = data.path("attachmentUrl").asText(); String name = url.substring(url.lastIndexOf('/') + 1);
        assertThat(Files.exists(Path.of("target/persistent-attachment-tests", name))).isFalse();
        assertThat(blobs.findById(name).orElseThrow().getContent()).isEqualTo(file().getBytes());
        mvc.perform(get("/uploads/{name}", name).header("Authorization", token(owner)))
                .andExpect(status().isOk()).andExpect(content().bytes(file().getBytes()));
        mvc.perform(get("/uploads/{name}", name)).andExpect(status().isUnauthorized());
        mvc.perform(get("/uploads/{name}", name).header("Authorization", token(outsider))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/files").param("fileUrl", url).header("Authorization", token(owner))).andExpect(status().isOk());
        assertThat(blobs.existsById(name)).isFalse();
        var message = messages.findById(data.path("id").asLong()).orElseThrow();
        assertThat(message.getContent()).isEqualTo("Keep this message");
        assertThat(message.getAttachmentUrl()).isNull();
        mvc.perform(get("/uploads/{name}", name).header("Authorization", token(owner))).andExpect(status().isNotFound());
    }

    @Test void failedTransactionDoesNotLeaveAttachmentBytesOrMessage() {
        var owner = account(); long channel = channel(owner); long before = blobs.count(); long beforeMessages = messages.count();
        transactions.executeWithoutResult(status -> {
            try { attachments.upload(owner.getEmail(), channel, file(), "rollback"); }
            catch (java.io.IOException e) { throw new IllegalStateException(e); }
            status.setRollbackOnly();
        });
        assertThat(blobs.count()).isEqualTo(before);
        assertThat(messages.count()).isEqualTo(beforeMessages);
    }

    @Test void legacyDiskAttachmentRemainsReadableWithoutChangingItsMessage() throws Exception {
        var owner = account(); long channel = channel(owner); String name = UUID.randomUUID() + ".txt";
        Path directory = Path.of("target/persistent-attachment-tests"); Files.createDirectories(directory);
        Path oldFile = directory.resolve(name); Files.write(oldFile, file().getBytes());
        var message = new com.chatapp.chatapp_backend.entity.Message();
        message.setChannel(channels.findById(channel).orElseThrow()); message.setSenderEmail(owner.getEmail());
        message.setSenderUsername(owner.getUsername()); message.setContent("Legacy message");
        message.setAttachmentName(name); message.setAttachmentUrl("http://localhost:8080/uploads/" + name);
        message.setAttachmentType("FILE"); messages.save(message);
        try {
            mvc.perform(get("/uploads/{name}", name).header("Authorization", token(owner)))
                    .andExpect(status().isOk()).andExpect(content().bytes(file().getBytes()));
            assertThat(blobs.existsById(name)).isFalse();
            assertThat(messages.findById(message.getId()).orElseThrow().getContent()).isEqualTo("Legacy message");
        } finally { Files.deleteIfExists(oldFile); }
    }
}
