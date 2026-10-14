package com.chatapp.chatapp_backend.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Locale;

@Component
public class GoogleTokenVerifier {
    private final String clientId;
    private volatile GoogleIdTokenVerifier verifier;
    public GoogleTokenVerifier(@Value("${app.auth.google-client-id:}") String clientId) { this.clientId=clientId; }
    public record Identity(String subject,String email,boolean authoritative) {}

    // Reuse the library's public-key cache, which honors Google's Cache-Control.
    private GoogleIdTokenVerifier verifier() throws Exception {
        if(verifier==null) synchronized(this) {
            if(verifier==null) verifier=new GoogleIdTokenVerifier.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .setIssuers(List.of("accounts.google.com","https://accounts.google.com"))
                .setAcceptableTimeSkewSeconds(0)
                .build();
        }
        return verifier;
    }
    public Identity verify(String token) {
        if(clientId.isBlank()) throw new IllegalStateException("Login Google indisponivel");
        if(token==null || token.isBlank() || token.length()>10000) throw new IllegalArgumentException("Token Google invalido");
        try {
            var verified=verifier().verify(token);
            if(verified==null) throw new IllegalArgumentException();
            var p=verified.getPayload();
            if(!Boolean.TRUE.equals(p.getEmailVerified()) || p.getSubject()==null || p.getSubject().isBlank()
                || p.getEmail()==null || p.getEmail().isBlank() || p.getExpirationTimeSeconds()==null
                || p.getExpirationTimeSeconds()<=java.time.Instant.now().getEpochSecond()) throw new IllegalArgumentException();
            String email=p.getEmail().toLowerCase(Locale.ROOT);
            return new Identity(p.getSubject(),email,email.endsWith("@gmail.com")
                || (p.getHostedDomain()!=null && !p.getHostedDomain().isBlank()));
        } catch(Exception e) { throw new IllegalArgumentException("Token Google invalido ou expirado"); }
    }
}
