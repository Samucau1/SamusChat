package com.chatapp.chatapp_backend.service;
import com.chatapp.chatapp_backend.entity.PasswordRecovery;
import com.chatapp.chatapp_backend.repository.*;
import com.chatapp.chatapp_backend.security.InputSanitizer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
@Service @RequiredArgsConstructor
public class PasswordRecoveryService {
    private final UserRepository users;
    private final PasswordRecoveryRepository recoveries;
    private final PasswordEncoder passwords;
    private final InputSanitizer sanitizer;
    private final AuthMailService mail;
    private final SecureRandom random=new SecureRandom();
    private String email(String input) {
        if(input==null || !sanitizer.isValidEmail(input.trim())) throw new IllegalArgumentException("Email invalido");
        return input.trim().toLowerCase(Locale.ROOT);
    }
    @Transactional
    public void request(String input) {
        String email=email(input);
        var user=users.lockedByEmail(email);
        if(user.isEmpty()) return;
        Instant now=Instant.now();
        var old=recoveries.findById(email);
        // User lock serializes resend, verify and reset across API instances.
        if(old.isPresent() && old.get().getSentAt().plusSeconds(600).isAfter(now)) return;
        String code=String.format(Locale.ROOT,"%04d",random.nextInt(10000));
        PasswordRecovery recovery=new PasswordRecovery();
        recovery.setEmail(email); recovery.setCodeHash(passwords.encode(code));
        recovery.setSentAt(now); recovery.setExpiresAt(now.plusSeconds(600));
        recoveries.save(recovery);
        mail.recovery(email,code);
    }
    @Transactional(noRollbackFor=IllegalArgumentException.class)
    public String verify(String input,String code) {
        String email=email(input);
        users.lockedByEmail(email).orElseThrow(PasswordRecoveryService::invalid);
        PasswordRecovery r=recoveries.findById(email).orElseThrow(PasswordRecoveryService::invalid);
        if(r.isConsumed() || r.getResetHash()!=null || r.getAttempts()>=5 || !r.getExpiresAt().isAfter(Instant.now())) throw invalid();
        r.setAttempts(r.getAttempts()+1); recoveries.save(r);
        if(code==null || !code.matches("[0-9]{4}") || !passwords.matches(code,r.getCodeHash())) throw invalid();
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        r.setResetHash(passwords.encode(token)); r.setExpiresAt(Instant.now().plusSeconds(300));
        recoveries.save(r); return token;
    }
    @Transactional
    public void reset(String input,String token,String password) {
        String email=email(input);
        if(password==null || !sanitizer.isValidPassword(password) || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("Senha deve ter 6 a 72 bytes");
        var user=users.lockedByEmail(email).orElseThrow(PasswordRecoveryService::invalid);
        var r=recoveries.findById(email).orElseThrow(PasswordRecoveryService::invalid);
        if(r.isConsumed() || r.getResetHash()==null || !r.getExpiresAt().isAfter(Instant.now()) || token==null || token.length()>100 || !passwords.matches(token,r.getResetHash())) throw invalid();
        user.setPassword(passwords.encode(password)); user.setAuthVersion(user.getAuthVersion()+1);
        users.save(user); r.setConsumed(true); r.setResetHash(null); recoveries.save(r);
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Codigo ou autorizacao invalida ou expirada"); }
}
