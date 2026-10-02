package com.chatapp.chatapp_backend.call;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Component
public class IceConfiguration {
    @Value("${app.calls.stun-url:}") private String stun;
    @Value("${app.calls.turn-url:}") private String turn;
    @Value("${app.calls.turn-secret:}") private String secret;
    public record Server(List<String> urls,String username,String credential) {}
    public List<Server> servers(String email) {
        var result=new ArrayList<Server>();
        if(!stun.isBlank())result.add(new Server(List.of(stun),"",""));
        if(!turn.isBlank()&&!secret.isBlank())try {
            // Coturn REST credentials: short-lived and account-specific, never expose the shared secret.
            String user=(Instant.now().getEpochSecond()+3600)+":"+UUID.nameUUIDFromBytes(email.getBytes(StandardCharsets.UTF_8));
            var mac=Mac.getInstance("HmacSHA1");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA1"));
            result.add(new Server(List.of(turn),user,Base64.getEncoder().encodeToString(mac.doFinal(user.getBytes(StandardCharsets.UTF_8)))));
        }catch(Exception e){throw new IllegalStateException("Configuracao TURN invalida",e);}
        return result;
    }
}
