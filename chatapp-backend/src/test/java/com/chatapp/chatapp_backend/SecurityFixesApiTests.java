package com.chatapp.chatapp_backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import java.nio.file.*;

@SpringBootTest @AutoConfigureMockMvc @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SecurityFixesApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired com.chatapp.chatapp_backend.repository.UserDeviceRepository devices;
    private record Account(String email, String token) {}
    private Account account() throws Exception {
        String name = "secure_" + UUID.randomUUID().toString().substring(0,8), email = name + "@example.test";
        var result = mvc.perform(post("/api/auth/register").contentType("application/json").content(json.writeValueAsString(Map.of("username",name,"email",email,"password","IntegrationTest123!"))))
            .andExpect(status().isOk()).andReturn();
        return new Account(email,json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText());
    }
    private long channel(Account owner) throws Exception {
        var result = mvc.perform(post("/api/servers").header("Authorization","Bearer " + owner.token).contentType("application/json").content("{\"name\":\"Security server\"}"))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("channels").get(0).path("id").asLong();
    }
    private String upload(Account owner,long channel,String name,String mime,byte[] bytes) throws Exception {
        var result=mvc.perform(multipart("/api/channels/{id}/upload",channel).file(new MockMultipartFile("file",name,mime,bytes)).header("Authorization","Bearer " + owner.token))
            .andExpect(status().isOk()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data").path("attachmentUrl").asText();
    }
    @Test void attachmentRequiresPermissionAndCannotBeDeletedByAnotherAccount() throws Exception {
        var owner=account(); var other=account(); long channel=channel(owner);
        byte[] bytes="Private attachment".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String url=upload(owner,channel,"private.txt","text/plain",bytes), path=java.net.URI.create(url).getPath();
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization","Bearer " + other.token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/files").param("fileUrl",url).header("Authorization","Bearer " + other.token)).andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization","Bearer " + owner.token)).andExpect(status().isOk()).andExpect(content().bytes(bytes)).andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment"))).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(delete("/api/files").param("fileUrl",url).header("Authorization","Bearer " + owner.token)).andExpect(status().isOk());
        mvc.perform(get(path).header("Authorization","Bearer " + owner.token)).andExpect(status().isNotFound());
    }
    @Test void disguisedHtmlAndFakeImageAreRejectedAndDeniedUploadLeavesNoFile() throws Exception {
        var owner=account(); var other=account(); long channel=channel(owner);
        Path root=Path.of("target/test-uploads"); Files.createDirectories(root);
        long before; try(var files=Files.list(root)){before=files.count();}
        for(var file:List.of(new MockMultipartFile("file","attack.html","text/plain","<script>1</script>".getBytes()),new MockMultipartFile("file","fake.png","image/png","not PNG".getBytes())))
            mvc.perform(multipart("/api/channels/{id}/upload",channel).file(file).header("Authorization","Bearer " + owner.token)).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/channels/{id}/upload",channel).file(new MockMultipartFile("file","audit.txt","text/plain","denied".getBytes())).header("Authorization","Bearer " + other.token)).andExpect(status().isForbidden());
        // A later message validation failure must roll back disk storage as well.
        mvc.perform(multipart("/api/channels/{id}/upload",channel).file(new MockMultipartFile("file","audit.txt","text/plain","rollback".getBytes())).param("content","x".repeat(2001)).header("Authorization","Bearer " + owner.token)).andExpect(status().isBadRequest());
        try(var files=Files.list(root)){assertThat(files.count()).isEqualTo(before);}
    }
    @Test void logoutRevokesAllIssuedTokensAndCannotBeCalledAnonymously() throws Exception {
        var owner=account();
        var login=mvc.perform(post("/api/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("email",owner.email,"password","IntegrationTest123!")))).andExpect(status().isOk()).andReturn();
        String second=json.readTree(login.getResponse().getContentAsString()).path("data").path("token").asText();
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").header("Authorization","Bearer " + owner.token)).andExpect(status().isOk());
        for(String token:List.of(owner.token,second)) mvc.perform(get("/api/users/me").header("Authorization","Bearer " + token)).andExpect(status().isUnauthorized());
    }
    @Test void accountLoginLimitSurvivesChangedForwardingHeaders() throws Exception {
        var owner=account();
        for(int i=0;i<5;i++) mvc.perform(post("/api/auth/login").header("X-Forwarded-For","198.18.1." + i).contentType("application/json").content(json.writeValueAsString(Map.of("email",owner.email,"password","wrong")))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").header("X-Forwarded-For","198.18.2.99").contentType("application/json").content(json.writeValueAsString(Map.of("email",owner.email,"password","IntegrationTest123!"))))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }
    @Test void weakPasswordRejectedAndFcmTokenCannotChangeOwner() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json").content("{\"username\":\"weakuser\",\"email\":\"weak@example.test\",\"password\":\"123456\"}")).andExpect(status().isBadRequest());
        var owner=account(); var other=account(); String push="push-" + UUID.randomUUID();
        for(var account:List.of(owner,other)) mvc.perform(post("/api/devices/register").header("Authorization","Bearer " + account.token).contentType("application/json").content(json.writeValueAsString(Map.of("fcmToken",push))))
            .andExpect(account.equals(owner)?status().isOk():status().isForbidden());
        mvc.perform(delete("/api/devices/unregister").header("Authorization","Bearer " + other.token).param("fcmToken",push)).andExpect(status().isForbidden());
        assertThat(devices.findByFcmToken(push).orElseThrow().getUserEmail()).isEqualTo(owner.email);
    }
    @Test void unsafeFilenameCannotReachOutsideStorage() throws Exception {
        var owner=account();
        mvc.perform(delete("/api/files").header("Authorization","Bearer " + owner.token).param("fileUrl","http://localhost/uploads/..%5C..%5Cconfig.txt")).andExpect(status().isNotFound());
    }
}
