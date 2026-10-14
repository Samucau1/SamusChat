package com.chatapp.chatapp_backend.call;

import com.chatapp.chatapp_backend.repository.*;
import com.chatapp.chatapp_backend.entity.Channel;
import com.chatapp.chatapp_backend.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class VoiceRoomService {
    private final VoicePresenceRepository presences;
    private final CallRepository calls;
    private final ChannelRepository channels;
    private final UserRepository users;
    private final PermissionService permissions;
    private final EntityManager entityManager;
    public record Room(Long channelId,List<CallService.Contact> participants,List<CallService.View> sessions,int capacity) {}

    private Channel channel(Long id,String email) {
        var channel=channels.findById(id).orElseThrow(()->new IllegalArgumentException("Canal inexistente"));
        permissions.requireMember(channel.getServer().getId(),email);
        if(!"VOICE".equals(channel.getType())) throw new IllegalArgumentException("Selecione um canal de chamada");
        entityManager.lock(channel,LockModeType.PESSIMISTIC_WRITE);
        return channel;
    }
    private void remove(VoicePresence presence) {
        for(var entry:calls.active(presence.getEmail())) if(presence.getChannelId().equals(entry.getChannelId())) {
            var call=calls.locked(entry.getId()).orElseThrow();
            call.setState("ENDED");call.setOffer(null);call.setAnswer(null);
        }
        presences.delete(presence);
    }
    private List<VoicePresence> live(Long id) {
        var result=new ArrayList<VoicePresence>();
        for(var p:presences.findByChannelIdOrderByEmail(id)) {
            if(p.getSeenAt().isBefore(Instant.now().minusSeconds(45))) remove(p); else result.add(p);
        }
        return result;
    }
    @Transactional
    public Room join(Long id,String email) {
        users.lockedByEmail(email).orElseThrow();
        channel(id,email);
        var previous=presences.findById(email);
        if(previous.isPresent() && previous.get().getSeenAt().isBefore(Instant.now().minusSeconds(45))) {
            remove(previous.get());presences.flush();previous=Optional.empty();
        }
        if(previous.isPresent() && !previous.get().getChannelId().equals(id)) throw new IllegalArgumentException("Saia do outro canal primeiro");
        var participants=live(id);
        if(previous.isEmpty()) {
            if(participants.size()>=6) throw new IllegalArgumentException("Canal cheio: limite de 6 participantes");
            if(calls.active(email).stream().anyMatch(c->!CallRules.expired(c,Instant.now()))) throw new IllegalArgumentException("Encerre a chamada atual primeiro");
            var presence=new VoicePresence();presence.setEmail(email);presence.setChannelId(id);presence.setSeenAt(Instant.now());presences.save(presence);
            for(var other:participants) {
                var call=new CallSession();call.setId(UUID.randomUUID().toString());call.setCaller(email);call.setCallee(other.getEmail());
                call.setChannelId(id);call.setState("CONNECTING");call.setCreatedAt(Instant.now());call.setCallerSeen(call.getCreatedAt());call.setCalleeSeen(call.getCreatedAt());calls.save(call);
            }
        }
        return snapshot(id,email);
    }
    @Transactional
    public Room snapshot(Long id,String email) {
        channel(id,email);
        var participants=live(id);
        var own=presences.findById(email).filter(p->p.getChannelId().equals(id)).orElseThrow(()->new SecurityException("Entre no canal primeiro"));
        own.setSeenAt(Instant.now());
        var sessions=calls.active(email).stream().filter(c->id.equals(c.getChannelId())).toList();
        for(var call:sessions) {if(call.getCaller().equals(email))call.setCallerSeen(Instant.now());else call.setCalleeSeen(Instant.now());}
        var contacts=participants.stream().map(p->users.findByEmail(p.getEmail()).orElseThrow()).map(u->new CallService.Contact(u.getEmail(),u.getUsername())).toList();
        return new Room(id,contacts,sessions.stream().map(CallService::view).toList(),6);
    }
    @Transactional
    public void leave(Long id,String email) {
        users.lockedByEmail(email).orElseThrow();
        channel(id,email);
        presences.findById(email).filter(p->p.getChannelId().equals(id)).ifPresent(this::remove);
    }
}
