package com.chatapp.chatapp_backend.service;
import com.chatapp.chatapp_backend.entity.User;
import com.chatapp.chatapp_backend.repository.UserRepository;
import com.chatapp.chatapp_backend.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
@Service @RequiredArgsConstructor
public class GoogleAuthService {
    private final GoogleTokenVerifier verifier;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtUtil jwt;
    @Transactional
    public String login(String idToken,String password) {
        var identity=verifier.verify(idToken);
        var linked=users.findByGoogleSubject(identity.subject());
        if(linked.isPresent()) {
            var user=users.lockedByEmail(linked.get().getEmail()).orElseThrow();
            return jwt.generateToken(user.getEmail());
        }
        var existing=users.lockedByEmail(identity.email());
        User user;
        if(existing.isPresent()) {
            user=existing.get();
            if(user.getGoogleSubject()!=null) throw new IllegalArgumentException("Conta Google diferente da vinculada");
            if(!identity.authoritative() && (password==null || !passwords.matches(password,user.getPassword())))
                throw new IllegalArgumentException("Confirme a senha atual para vincular este email ao Google");
        } else {
            user=new User(); user.setEmail(identity.email());
            user.setUsername("google_"+UUID.randomUUID().toString().replace("-", "").substring(0,20));
            user.setPassword(passwords.encode(UUID.randomUUID().toString()));
        }
        user.setGoogleSubject(identity.subject()); users.saveAndFlush(user);
        return jwt.generateToken(user.getEmail());
    }
}
