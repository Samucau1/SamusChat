package com.chatapp.chatapp_backend.call;

import com.chatapp.chatapp_backend.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class CallService {
    private final CallRepository calls;
    private final UserRepository users;
    private final VoicePresenceRepository presences;
    private final com.chatapp.chatapp_backend.repository.ChannelRepository channels;
    private final com.chatapp.chatapp_backend.service.PermissionService permissions;
    public record Contact(String email,String username) {}
    public record View(String id,String caller,String callee,String state,String offer,String answer,Long channelId) {}
    public static View view(CallSession c) { return new View(c.getId(),c.getCaller(),c.getCallee(),c.getState(),c.getOffer(),c.getAnswer(),c.getChannelId()); }
    @Transactional(readOnly=true)
    public List<Contact> contacts(String email) {
        return users.findTop100ByEmailNotOrderByUsernameAsc(email).stream().map(u->new Contact(u.getEmail(),u.getUsername())).toList();
    }
    private void expire(CallSession c) {
        if(CallRules.expired(c,Instant.now())) { c.setState("EXPIRED");c.setOffer(null);c.setAnswer(null); }
    }
    @Transactional
    public View invite(String caller,String callee,String requestId) {
        if(callee==null || caller.equals(callee))throw new IllegalArgumentException("Selecione outro contato");
        UUID.fromString(requestId);
        // A stable order serializes concurrent invitations for either participant across API instances.
        var emails=new ArrayList<>(List.of(caller,callee));Collections.sort(emails);
        for(String email:emails)users.lockedByEmail(email).orElseThrow(()->new IllegalArgumentException("Contato indisponivel"));
        var existing=calls.findById(requestId);
        if(existing.isPresent()) {
            var c=existing.get();
            if(!c.getCaller().equals(caller)||!c.getCallee().equals(callee))throw new SecurityException("Chamada privada");
            return view(c);
        }
        for(String email:emails) if(presences.findById(email).filter(p->p.getSeenAt().isAfter(Instant.now().minusSeconds(45))).isPresent())
            throw new IllegalArgumentException("Usuario ocupado em um canal de chamada");
        for(String email:emails)for(var stale:calls.active(email)) {
            var c=calls.locked(stale.getId()).orElseThrow();expire(c);
            if(!c.terminal())throw new IllegalArgumentException("Usuario ocupado em outra chamada");
        }
        var c=new CallSession();c.setId(requestId);c.setCaller(caller);c.setCallee(callee);c.setState("RINGING");
        c.setCreatedAt(Instant.now());c.setCallerSeen(c.getCreatedAt());c.setCalleeSeen(c.getCreatedAt());
        return view(calls.save(c));
    }
    @Transactional
    public List<View> current(String email) {
        var result=new ArrayList<View>();
        for(var entry:calls.active(email)) {
            if(entry.getChannelId()!=null) continue;
            var c=calls.locked(entry.getId()).orElseThrow();expire(c);
            if(!c.terminal()) {touch(c,email);result.add(view(c));}
        }
        return result;
    }
    @Transactional
    public View get(String id,String email) {
        var c=calls.locked(id).orElseThrow(()->new IllegalArgumentException("Chamada inexistente"));
        authorize(c,email);expire(c);if(!c.terminal())touch(c,email);return view(c);
    }
    private void touch(CallSession c,String email) {
        if(c.getCaller().equals(email))c.setCallerSeen(Instant.now());else c.setCalleeSeen(Instant.now());
    }
    @Transactional
    public View action(String id,String email,String action,String sdp) {
        var c=calls.locked(id).orElseThrow(()->new IllegalArgumentException("Chamada inexistente"));
        authorize(c,email);expire(c);
        if(c.terminal())return view(c);
        switch(action) {
            case "accept" -> {
                if(!email.equals(c.getCallee()))throw new SecurityException("Somente destinatario pode aceitar");
                if(c.getState().equals("RINGING"))c.setState("CONNECTING");
            }
            case "decline" -> {
                if(!email.equals(c.getCallee()) || !c.getState().equals("RINGING"))throw new IllegalArgumentException("Recusa invalida");
                c.setState("DECLINED");
            }
            case "end" -> c.setState("ENDED");
            case "offer" -> {
                if(!email.equals(c.getCaller()) || !c.getState().equals("CONNECTING"))throw new IllegalArgumentException("Oferta invalida");
                CallRules.sdp(sdp,"offer");
                if(c.getOffer()!=null && !c.getOffer().equals(sdp))throw new IllegalArgumentException("Oferta ja enviada");
                c.setOffer(sdp);
            }
            case "answer" -> {
                if(!email.equals(c.getCallee()) || c.getOffer()==null || !(c.getState().equals("CONNECTING")||c.getState().equals("ACTIVE")))throw new IllegalArgumentException("Resposta invalida");
                CallRules.sdp(sdp,"answer");
                if(c.getAnswer()!=null && !c.getAnswer().equals(sdp))throw new IllegalArgumentException("Resposta ja enviada");
                c.setAnswer(sdp);c.setState("ACTIVE");
            }
            default -> throw new IllegalArgumentException("Acao invalida");
        }
        touch(c,email);
        if(c.terminal()){c.setOffer(null);c.setAnswer(null);}
        return view(c);
    }
    private void authorize(CallSession call,String email) {
        CallRules.participant(call,email);
        if(call.getChannelId()!=null) {
            var channel=channels.findById(call.getChannelId()).orElseThrow();
            permissions.requireMember(channel.getServer().getId(),email);
            if(!call.terminal() && presences.findById(email).filter(p->p.getChannelId().equals(call.getChannelId()) && p.getSeenAt().isAfter(Instant.now().minusSeconds(45))).isEmpty())
                throw new SecurityException("Entre no canal para usar a chamada");
        }
    }
}
