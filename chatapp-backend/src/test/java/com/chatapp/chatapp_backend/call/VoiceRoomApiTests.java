package com.chatapp.chatapp_backend.call;

import com.chatapp.chatapp_backend.entity.*;
import com.chatapp.chatapp_backend.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class VoiceRoomApiTests {
    @Autowired MockMvc api; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired ServerRepository servers; @Autowired ServerMemberRepository members;
    @Autowired ChannelRepository channels; @Autowired VoicePresenceRepository presences; @Autowired CallRepository calls;
    Long serverId, voiceId, textId;
    @BeforeEach void setup() {
        for(var name:List.of("a","b","c","outsider")) account(name);
        var server=new Server();server.setName("Rooms");server.setOwnerEmail("a@room.test");servers.save(server);serverId=server.getId();
        for(var name:List.of("a","b","c")) member(name,server);
        var voice=new Channel();voice.setName("Lounge");voice.setType("VOICE");voice.setServer(server);channels.save(voice);voiceId=voice.getId();
        var text=new Channel();text.setName("Chat");text.setServer(server);channels.save(text);textId=text.getId();
    }
    void account(String name){var u=new User();u.setEmail(name+"@room.test");u.setUsername(name+UUID.randomUUID());u.setPassword("unused");users.save(u);}
    void member(String name,Server server){var m=new ServerMember();m.setUserEmail(name+"@room.test");m.setServer(server);m.setRole(name.equals("a")?"ADMIN":"USER");members.save(m);}
    org.springframework.test.web.servlet.ResultActions join(String who)throws Exception{return api.perform(post("/api/channels/"+voiceId+"/call/join").with(user(who+"@room.test")));}
    @Test void threeMembersGetOnlyTheirOwnMeshSessions()throws Exception {
        join("a").andExpect(jsonPath("$.data.participants.length()").value(1));
        join("b").andExpect(jsonPath("$.data.sessions.length()").value(1));
        join("c").andExpect(jsonPath("$.data.participants.length()").value(3)).andExpect(jsonPath("$.data.sessions.length()").value(2));
        assertThat(calls.count()).isEqualTo(3);
        api.perform(get("/api/channels/"+voiceId+"/call").with(user("a@room.test"))).andExpect(jsonPath("$.data.sessions.length()").value(2));
        api.perform(get("/api/calls").with(user("a@room.test"))).andExpect(jsonPath("$.data.length()").value(0));
        join("a").andExpect(status().isOk());assertThat(calls.count()).isEqualTo(3);
    }
    @Test void nonMembersAndTextChannelsCannotBeJoined()throws Exception {
        join("outsider").andExpect(status().isBadRequest());
        api.perform(post("/api/channels/"+textId+"/call/join").with(user("a@room.test"))).andExpect(status().isBadRequest());
        api.perform(post("/api/channels/"+voiceId+"/call/join")).andExpect(status().isUnauthorized());
        api.perform(get("/api/channels/"+voiceId+"/call").with(user("a@room.test"))).andExpect(status().isUnauthorized());
    }
    @Test void thirdMemberCannotAccessAnotherPairsSdp()throws Exception {
        join("a");join("b");join("c");
        var ab=calls.active("a@room.test").stream().filter(c->c.getCaller().equals("b@room.test")).findFirst().orElseThrow();
        api.perform(get("/api/calls/"+ab.getId()).with(user("c@room.test"))).andExpect(status().isUnauthorized());
    }
    @Test void leaveEndsOnlyOwnLinksAndIsIdempotent()throws Exception {
        join("a");join("b");join("c");
        for(int i=0;i<2;i++)api.perform(post("/api/channels/"+voiceId+"/call/leave").with(user("c@room.test"))).andExpect(status().isOk());
        assertThat(calls.active("c@room.test")).isEmpty();assertThat(calls.active("a@room.test")).hasSize(1);
        assertThat(presences.count()).isEqualTo(2);
    }
    @Test void stalePresenceIsRemovedAlongWithItsMediaLinks()throws Exception {
        join("a");join("b");presences.findById("b@room.test").orElseThrow().setSeenAt(Instant.now().minusSeconds(46));
        api.perform(get("/api/channels/"+voiceId+"/call").with(user("a@room.test"))).andExpect(jsonPath("$.data.participants.length()").value(1)).andExpect(jsonPath("$.data.sessions.length()").value(0));
        assertThat(presences.findById("b@room.test")).isEmpty();
    }
    @Test void roomOccupancyBlocksIndividualInvitesEvenWhenAlone()throws Exception {
        join("a");
        api.perform(post("/api/calls").with(user("outsider@room.test")).contentType("application/json").content(json.writeValueAsString(Map.of("callee","a@room.test","requestId",UUID.randomUUID().toString())))).andExpect(status().isBadRequest());
    }
    @Test void individualCallBlocksRoomEntry()throws Exception {
        api.perform(post("/api/calls").with(user("outsider@room.test")).contentType("application/json").content(json.writeValueAsString(Map.of("callee","a@room.test","requestId",UUID.randomUUID().toString())))).andExpect(status().isOk());
        join("a").andExpect(status().isBadRequest());
    }
    @Test void roomCapacityIsEnforced()throws Exception {
        join("a");join("b");join("c");
        var server=servers.findById(serverId).orElseThrow();
        for(var name:List.of("d","e","f","g")){account(name);member(name,server);}
        join("d");join("e");join("f");join("g").andExpect(status().isBadRequest());assertThat(presences.count()).isEqualTo(6);
    }
    @Test void channelCreationValidatesTypeNameAndAdminRole()throws Exception {
        var url="/api/servers/"+serverId+"/channels";
        for(var type:List.of("TEXT","VOICE"))api.perform(post(url).with(user("a@room.test")).contentType("application/json").content(json.writeValueAsString(Map.of("name","new-"+type,"type",type)))).andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value(type));
        api.perform(post(url).with(user("b@room.test")).contentType("application/json").content("{\"name\":\"forbidden\",\"type\":\"VOICE\"}")).andExpect(status().isBadRequest());
        api.perform(post(url).with(user("a@room.test")).contentType("application/json").content("{\"name\":\"bad\",\"type\":\"INVALID\"}")).andExpect(status().isBadRequest());
        api.perform(post(url).with(user("a@room.test")).contentType("application/json").content(json.writeValueAsString(Map.of("name","x".repeat(256),"type","TEXT")))).andExpect(status().isBadRequest());
    }
    @Test void roomHeartbeatsHaveTheirOwnBudgetWithoutExhaustingMessageRequests()throws Exception {
        String ip="127.0.0.50";
        api.perform(post("/api/channels/"+voiceId+"/call/join").header("X-Forwarded-For",ip).with(user("a@room.test"))).andExpect(status().isOk());
        for(int i=0;i<185;i++)api.perform(get("/api/channels/"+voiceId+"/call").header("X-Forwarded-For",ip).with(user("a@room.test"))).andExpect(status().isOk());
        api.perform(get("/api/servers").header("X-Forwarded-For",ip).with(user("a@room.test"))).andExpect(status().isOk());
    }
}
