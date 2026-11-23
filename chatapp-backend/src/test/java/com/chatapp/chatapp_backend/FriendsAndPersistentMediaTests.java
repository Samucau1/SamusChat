package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.*;
import com.chatapp.chatapp_backend.security.*;
import com.chatapp.chatapp_backend.service.FriendService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="file.upload-dir=./target/social-media-uploads") @AutoConfigureMockMvc
class FriendsAndPersistentMediaTests {
    @Autowired MockMvc mvc; @Autowired ObjectMapper json; @Autowired UserRepository users;
    @Autowired JwtUtil jwt; @Autowired PublicIdentityService identities; @Autowired FriendService service;
    @Autowired FriendshipRepository friendships; @Autowired AppearanceBlobRepository blobs;
    record Account(User user,String token,String id) {}
    Account account(String prefix) {
        var u=new User();u.setUsername(prefix+UUID.randomUUID().toString().substring(0,8));u.setEmail(UUID.randomUUID()+"@private.test");u.setPassword("not-used-in-these-tests");users.save(u);
        return new Account(u,"Bearer "+jwt.generateToken(u.getEmail()),identities.publicId(u.getEmail()));
    }
    String response(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();}
    void friend(Account a,Account b)throws Exception{response(post("/api/friends/"+b.id()+"/request").header("Authorization",a.token()));response(post("/api/friends/"+a.id()+"/accept").header("Authorization",b.token()));}
    @Test void discoveryIsSeparateFromFriendsAndRequiresRecipientAcceptance() throws Exception {
        var a=account("discover");var b=account("lookup");var stranger=account("outsider");
        assertThat(response(get("/api/calls/contacts").header("Authorization",a.token()))).doesNotContain(b.id(),stranger.id());
        String search=response(get("/api/friends/search").param("q",b.user().getUsername()).header("Authorization",a.token()));
        assertThat(search).contains(b.id(),"NONE").doesNotContain(b.user().getEmail(),"password","googleSubject");
        assertThat(response(get("/api/friends/search").param("q",b.id()).header("Authorization",a.token()))).contains(b.id());
        response(post("/api/friends/"+b.id()+"/request").header("Authorization",a.token()));
        assertThat(response(get("/api/friends").header("Authorization",a.token()))).contains("OUTGOING");
        assertThat(response(get("/api/friends").header("Authorization",b.token()))).contains("INCOMING");
        assertThat(response(get("/api/calls/contacts").header("Authorization",a.token()))).doesNotContain(b.id());
        mvc.perform(post("/api/friends/"+b.id()+"/accept").header("Authorization",a.token())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/friends/"+a.id()+"/accept").header("Authorization",stranger.token())).andExpect(status().isBadRequest());
        response(post("/api/friends/"+a.id()+"/accept").header("Authorization",b.token()));
        assertThat(response(get("/api/calls/contacts").header("Authorization",a.token()))).contains(b.id()).doesNotContain(stranger.id(),b.user().getEmail());
        assertThat(response(get("/api/calls/contacts").header("Authorization",b.token()))).contains(a.id());
    }
    @Test void removingFriendKeepsConversationAndDoesNotCreateAutomaticFriendships() throws Exception {
        var a=account("history");var b=account("history");friend(a,b);
        response(post("/api/direct-messages").param("contact",b.id()).header("Authorization",a.token()).contentType("application/json").content("{\"content\":\"Conversa preservada\"}"));
        response(delete("/api/friends/"+b.id()).header("Authorization",a.token()));
        assertThat(response(get("/api/friends").header("Authorization",a.token()))).doesNotContain(b.id());
        assertThat(response(get("/api/direct-messages/contacts").header("Authorization",a.token()))).contains(b.id()).doesNotContain(b.user().getEmail());
        assertThat(response(get("/api/direct-messages").param("contact",a.id()).header("Authorization",b.token()))).contains("Conversa preservada");
        mvc.perform(post("/api/friends/"+a.id()+"/request").header("Authorization",a.token())).andExpect(status().isBadRequest());
        mvc.perform(post("/api/friends/not-a-user/request").header("Authorization",a.token())).andExpect(status().isBadRequest());
    }
    @Test void profileShowsAppearanceButNeverLoginData() throws Exception {
        var a=account("profile");var b=account("viewer");
        response(put("/api/users/me/appearance").header("Authorization",a.token()).contentType("application/json").content("{\"bio\":\"Programação e música\",\"accentColor\":\"#cc3344\"}"));
        String profile=response(get("/api/friends/profiles/"+a.id()).header("Authorization",b.token()));
        assertThat(profile).contains("Programação e música","#CC3344",a.id(),a.user().getUsername()).doesNotContain(a.user().getEmail(),"password","googleSubject");
        mvc.perform(get("/api/friends/profiles/"+a.id())).andExpect(status().isUnauthorized());
    }
    @Test void oppositeConcurrentRequestsRemainOnePendingRelationship() throws Exception {
        var a=account("race");var b=account("race");var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {var first=pool.submit(()->{gate.await();return service.change(a.user().getEmail(),b.id(),"request");});var second=pool.submit(()->{gate.await();return service.change(b.user().getEmail(),a.id(),"request");});gate.countDown();first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
            var rows=friendships.forUser(a.user().getId());assertThat(rows).hasSize(1);assertThat(rows.getFirst().isAccepted()).isFalse();
        }finally{pool.shutdownNow();}
    }
    MockMultipartFile image() throws Exception {var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(40,40,BufferedImage.TYPE_INT_RGB),"png",out);return new MockMultipartFile("file","test.png","image/png",out.toByteArray());}
    @Test void photoIsServedFromDatabaseAfterLocalFileIsGoneAndTokenIsRenewed() throws Exception {
        var a=account("persistent");var uploaded=response(multipart("/api/users/me/media/avatar").file(image()).header("Authorization",a.token()));
        String url=json.readTree(uploaded).path("data").path("avatarUrl").asText(),filename=url.substring(url.lastIndexOf('/')+1);
        byte[] stored=blobs.findById(filename).orElseThrow().getContent();
        Files.deleteIfExists(Paths.get("target/social-media-uploads/appearance",filename));
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg")).andExpect(content().bytes(stored));
        String next="Bearer "+jwt.generateToken(a.user().getEmail());
        assertThat(response(get("/api/users/me").header("Authorization",next))).contains(url);
        var rejected=new MockMultipartFile("file","fake.png","image/png","not an image".getBytes());
        mvc.perform(multipart("/api/users/me/media/avatar").file(rejected).header("Authorization",next)).andExpect(status().isBadRequest());
        assertThat(response(get("/api/users/me").header("Authorization",next))).contains(url);
        mvc.perform(get(url)).andExpect(status().isOk()).andExpect(content().bytes(stored));
        response(delete("/api/users/me/media/avatar").header("Authorization",next));
        assertThat(blobs.existsById(filename)).isFalse();mvc.perform(get(url)).andExpect(status().isNotFound());
    }
}
