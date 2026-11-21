package com.chatapp.chatapp_backend.service;

import com.chatapp.chatapp_backend.entity.*;
import com.chatapp.chatapp_backend.repository.*;
import com.chatapp.chatapp_backend.security.PublicIdentityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class FriendService {
    private final UserRepository users;
    private final FriendshipRepository friendships;
    private final PublicIdentityService identities;
    public record Profile(String publicId,String username,String avatarUrl,String bannerUrl,String backgroundUrl,String accentColor,String bio,String relationship) {}
    private User me(String email) { return users.findByEmail(email).orElseThrow(()->new SecurityException("Conta indisponível")); }
    private User target(String id) {
        if(id==null || !id.matches("usr_[1-9][0-9]*_[A-Za-z0-9_-]{22}")) throw new IllegalArgumentException("ID de usuário inválido");
        return me(identities.resolve(id));
    }
    private Optional<Friendship> pair(User a,User b) { return friendships.findByLowUserIdAndHighUserId(Math.min(a.getId(),b.getId()),Math.max(a.getId(),b.getId())); }
    private String status(User viewer,User other,Friendship f) {
        if(viewer.getId().equals(other.getId())) return "SELF";
        if(f==null) return "NONE";
        return f.isAccepted()?"FRIEND":f.getRequestedBy().equals(viewer.getId())?"OUTGOING":"INCOMING";
    }
    private Profile view(User viewer,User other,Friendship f) { return new Profile(identities.publicId(other.getEmail()),other.getUsername(),other.getAvatarUrl(),other.getBannerUrl(),other.getBackgroundUrl(),other.getAccentColor(),other.getBio(),status(viewer,other,f)); }
    @Transactional(readOnly=true) public Profile profile(String email,String id) { var viewer=me(email);var other=target(id);return view(viewer,other,pair(viewer,other).orElse(null)); }
    @Transactional(readOnly=true) public List<Profile> list(String email) {
        var viewer=me(email);
        return friendships.forUser(viewer.getId()).stream().map(f->{var other=users.findById(f.getLowUserId().equals(viewer.getId())?f.getHighUserId():f.getLowUserId()).orElseThrow();return view(viewer,other,f);}).toList();
    }
    @Transactional(readOnly=true) public List<Profile> search(String email,String query) {
        var viewer=me(email);String q=query==null?"":query.strip();
        if(q.length()<2) return List.of();
        if(q.length()>100) throw new IllegalArgumentException("Busque por nome ou ID de até 100 caracteres");
        List<User> found;
        if(q.startsWith("usr_")) {try{found=List.of(target(q));}catch(IllegalArgumentException e){return List.of();}}
        else found=users.searchNames(viewer.getId(),q,org.springframework.data.domain.PageRequest.of(0,20));
        return found.stream().filter(u->!u.getId().equals(viewer.getId())).map(u->view(viewer,u,pair(viewer,u).orElse(null))).toList();
    }
    @Transactional public Profile change(String email,String id,String action) {
        var viewer=me(email);var other=target(id);
        if(viewer.getId().equals(other.getId())) throw new IllegalArgumentException("Escolha outra pessoa");
        // Stable row locks serialize both directions, including first requests.
        var sorted=new ArrayList<>(List.of(viewer,other));sorted.sort(Comparator.comparing(User::getId));
        for(var u:sorted) users.lockedByEmail(u.getEmail()).orElseThrow();
        var f=pair(viewer,other).orElse(null);
        switch(action) {
            case "request" -> {
                if(f==null) {f=new Friendship();f.setLowUserId(sorted.get(0).getId());f.setHighUserId(sorted.get(1).getId());f.setRequestedBy(viewer.getId());friendships.save(f);}
            }
            case "accept" -> {
                if(f==null || f.getRequestedBy().equals(viewer.getId())) throw new IllegalArgumentException("Solicitação recebida não encontrada");
                f.setAccepted(true);friendships.save(f);
            }
            case "remove" -> {if(f!=null) friendships.delete(f);f=null;}
            default -> throw new IllegalArgumentException("Ação inválida");
        }
        return view(viewer,other,f);
    }
}
