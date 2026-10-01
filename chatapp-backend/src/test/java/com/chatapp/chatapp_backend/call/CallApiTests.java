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
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class CallApiTests {
    @Autowired MockMvc api; @Autowired ObjectMapper json;
    @Autowired UserRepository users; @Autowired ServerRepository servers;
    @Autowired ServerMemberRepository members; @Autowired CallRepository calls;
    String id;
    @BeforeEach void setup(){
        id=UUID.randomUUID().toString();
        for(String name:List.of("a","b","outsider")){var u=new User();u.setUsername(name+id);u.setEmail(name+"@call.test");u.setPassword("unused");users.save(u);}
        var s=new Server();s.setName("calls");s.setOwnerEmail("a@call.test");servers.save(s);
        for(String name:List.of("a","b")){var m=new ServerMember();m.setServer(s);m.setUserEmail(name+"@call.test");members.save(m);}
    }
    void invite() throws Exception {
        api.perform(post("/api/calls").with(user("a@call.test")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("callee","b@call.test","requestId",id))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.state").value("RINGING"));
    }
    org.springframework.test.web.servlet.ResultActions action(String who,String action,String sdp)throws Exception{
        var payload=new HashMap<String,String>();payload.put("action",action);if(sdp!=null)payload.put("sdp",sdp);
        return api.perform(post("/api/calls/"+id).with(user(who+"@call.test")).contentType("application/json").content(json.writeValueAsString(payload)));
    }
    @Test void lifecycleAndIdempotentInvite()throws Exception{
        invite();invite();assertThat(calls.count()).isEqualTo(1);
        action("b","accept",null).andExpect(jsonPath("$.data.state").value("CONNECTING"));
        action("a","offer","v=0\nm=audio 9 RTP/SAVPF 111").andExpect(status().isOk());
        action("b","answer","v=0\nm=audio 9 RTP/SAVPF 111").andExpect(jsonPath("$.data.state").value("ACTIVE"));
        action("a","end",null).andExpect(jsonPath("$.data.state").value("ENDED")).andExpect(jsonPath("$.data.offer").doesNotExist());
        action("b","end",null).andExpect(jsonPath("$.data.state").value("ENDED"));
    }
    @Test void rejectsOutsiderAndCallerAccept()throws Exception{
        invite();api.perform(get("/api/calls/"+id).with(user("outsider@call.test"))).andExpect(status().isUnauthorized());
        action("outsider","end",null).andExpect(status().isUnauthorized());action("a","accept",null).andExpect(status().isUnauthorized());
    }
    @Test void contactsAndInvitesRequireSharedServer()throws Exception{
        api.perform(get("/api/calls/contacts").with(user("a@call.test"))).andExpect(jsonPath("$.data.length()").value(1));
        api.perform(post("/api/calls").with(user("outsider@call.test")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("callee","b@call.test","requestId",id)))).andExpect(status().isUnauthorized());
    }
    @Test void busyAndDecline()throws Exception{
        invite();api.perform(post("/api/calls").with(user("b@call.test")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("callee","a@call.test","requestId",UUID.randomUUID().toString())))).andExpect(status().isBadRequest());
        action("b","decline",null).andExpect(jsonPath("$.data.state").value("DECLINED"));
    }
    @Test void rejectsAnswerBeforeOfferAndWrongOfferSender()throws Exception{
        invite();action("b","accept",null);action("b","answer","v=0\nm=audio").andExpect(status().isBadRequest());
        action("b","offer","v=0\nm=audio").andExpect(status().isBadRequest());
    }
    @Test void expiredCallCannotBeAccepted()throws Exception{
        invite();calls.findById(id).orElseThrow().setCreatedAt(java.time.Instant.now().minusSeconds(46));
        action("b","accept",null).andExpect(jsonPath("$.data.state").value("EXPIRED"));
    }
    @Test void anonymousCannotReadCalls()throws Exception{api.perform(get("/api/calls")).andExpect(status().isUnauthorized());}
}
