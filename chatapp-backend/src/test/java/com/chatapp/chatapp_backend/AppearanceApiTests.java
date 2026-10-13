package com.chatapp.chatapp_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties = "file.upload-dir=./target/appearance-test-uploads")
@AutoConfigureMockMvc
class AppearanceApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String register(String name) throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        var response = mvc.perform(post("/api/auth/register").header("X-Forwarded-For", "test-" + UUID.randomUUID()).contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("username", name + suffix, "email", name + suffix + "@example.com", "password", "IntegrationTest123!"))))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(response.getResponse().getContentAsString()).path("data").path("token").asText();
    }
    MockMultipartFile image(String format, int width, int height) throws Exception {
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, bytes);
        return new MockMultipartFile("file", "untrusted-name.html", "image/" + format, bytes.toByteArray());
    }
    @Test void profileAppearancePersistsAndRejectsInvalidInput() throws Exception {
        String token = register("profile");
        mvc.perform(put("/api/users/me/appearance").header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID()).contentType("application/json").content("{\"bio\":\" Meu perfil \" ,\"accentColor\":\"#aabbcc\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bio").value("Meu perfil")).andExpect(jsonPath("$.data.accentColor").value("#AABBCC"));
        mvc.perform(get("/api/users/me").header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(jsonPath("$.data.bio").value("Meu perfil"));
        for (String body : java.util.List.of("{\"bio\":\"<script>\"}", "{\"accentColor\":\"red\"}", json.writeValueAsString(java.util.Map.of("bio", "a".repeat(191)))))
            mvc.perform(put("/api/users/me/appearance").header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me").header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(jsonPath("$.data.bio").value("Meu perfil"));
    }
    @Test void usernameAllowsDisplayNamesTrimsAndRejectsOversizedOrUnsafeValues() throws Exception {
        String token = register("name");
        String display = "João " + UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < 2; i++) mvc.perform(put("/api/users/me/username").param("newUsername", " " + display + " ").header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(display));
        for (String name : java.util.List.of("", "  ", "a".repeat(51), "<b>nome</b>", "nome\nnovo"))
            mvc.perform(put("/api/users/me/username").param("newUsername", name).header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isBadRequest());
        mvc.perform(get("/api/users/me").header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(jsonPath("$.data.username").value(display));
    }
    @Test void avatarIsResizedReplacedAndRemovedAndGifHasStaticFallback() throws Exception {
        String token = register("media");
        var result = mvc.perform(multipart("/api/users/me/media/avatar").file(image("png", 1000, 700)).header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(endsWith(".jpg"))).andReturn();
        String url = json.readTree(result.getResponse().getContentAsString()).path("data").path("avatarUrl").asText();
        org.junit.jupiter.api.Assertions.assertTrue(url.startsWith("/uploads/appearance/"), "Media must use the client's API origin on both PC and Android");
        java.nio.file.Path path = java.nio.file.Paths.get("target/appearance-test-uploads/appearance", url.substring(url.lastIndexOf('/') + 1));
        org.junit.jupiter.api.Assertions.assertEquals(256, ImageIO.read(path.toFile()).getWidth());
        result = mvc.perform(multipart("/api/users/me/media/avatar").file(image("gif", 100, 100)).header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").value(endsWith(".gif"))).andReturn();
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(path));
        url = json.readTree(result.getResponse().getContentAsString()).path("data").path("avatarUrl").asText();
        String filename = url.substring(url.lastIndexOf('/') + 1);
        org.junit.jupiter.api.Assertions.assertTrue(java.nio.file.Files.exists(path.getParent().resolve(filename.replace(".gif", ".png"))));
        mvc.perform(delete("/api/users/me/media/avatar").header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isOk()).andExpect(jsonPath("$.data.avatarUrl").doesNotExist());
        org.junit.jupiter.api.Assertions.assertFalse(java.nio.file.Files.exists(path.getParent().resolve(filename)));
        mvc.perform(multipart("/api/users/me/media/avatar").file(image("gif", 600, 600)).header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isBadRequest());
        byte[] largeCanvas = image("gif", 1, 1).getBytes();
        largeCanvas[6] = (byte) 0xff; largeCanvas[7] = 0x7f;
        mvc.perform(multipart("/api/users/me/media/avatar").file(new MockMultipartFile("file", "large.gif", "image/gif", largeCanvas)).header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/users/me/media/avatar").file(new MockMultipartFile("file", "fake.png", "image/png", "not an image".getBytes())).header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/users/me/media/nope").file(image("png", 1, 1)).header("Authorization", token).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/users/me/media/avatar").file(image("png", 1, 1))).andExpect(status().isUnauthorized());
    }
    @Test void serverAppearanceIsRestrictedToAdminAndVisibleToMembers() throws Exception {
        String admin = register("admin"), member = register("member");
        var result = mvc.perform(post("/api/servers").header("Authorization", admin).header("X-Forwarded-For", "appearance-" + UUID.randomUUID()).contentType("application/json").content("{\"name\":\"Custom\",\"description\":\"Teste\"}")).andExpect(status().isOk()).andReturn();
        long id = json.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
        mvc.perform(post("/api/servers/{id}/join", id).header("Authorization", member).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isOk());
        mvc.perform(put("/api/servers/{id}/appearance", id).header("Authorization", member).header("X-Forwarded-For", "appearance-" + UUID.randomUUID()).contentType("application/json").content("{\"accentColor\":\"#123456\"}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/servers/{id}/media/icon", id).file(image("png", 20, 20)).header("Authorization", member).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isForbidden());
        mvc.perform(put("/api/servers/{id}/appearance", id).header("Authorization", admin).header("X-Forwarded-For", "appearance-" + UUID.randomUUID()).contentType("application/json").content("{\"accentColor\":\"#123456\"}")).andExpect(status().isOk());
        for (String slot : java.util.List.of("icon", "banner", "background")) {
            mvc.perform(multipart("/api/servers/{id}/media/{slot}", id, slot).file(image("png", 20, 20)).header("Authorization", admin).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isOk());
            mvc.perform(get("/api/servers/{id}", id).header("Authorization", member).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(jsonPath("$.data." + slot + "Url").isString());
            mvc.perform(delete("/api/servers/{id}/media/{slot}", id, slot).header("Authorization", admin).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(status().isOk()).andExpect(jsonPath("$.data." + slot + "Url").doesNotExist());
        }
        mvc.perform(get("/api/servers/{id}", id).header("Authorization", member).header("X-Forwarded-For", "appearance-" + UUID.randomUUID())).andExpect(jsonPath("$.data.accentColor").value("#123456"));
    }
}
