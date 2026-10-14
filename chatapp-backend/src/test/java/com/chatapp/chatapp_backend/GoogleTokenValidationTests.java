package com.chatapp.chatapp_backend;

import com.chatapp.chatapp_backend.service.GoogleTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.json.webtoken.JsonWebSignature;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.security.*;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GoogleTokenValidationTests {
    static KeyPair trusted,untrusted;
    GoogleTokenVerifier service;
    @BeforeAll static void keys() throws Exception {
        var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        trusted=generator.generateKeyPair(); untrusted=generator.generateKeyPair();
    }
    @BeforeEach void setup() {
        service=new GoogleTokenVerifier("our-client");
        var verifier=(GoogleIdTokenVerifier)ReflectionTestUtils.invokeMethod(service,"verifier");
        // Local RSA fixture keys: exercise real signature checks without contacting Google.
        ReflectionTestUtils.setField(verifier.getPublicKeysManager(),"publicKeys",List.of(trusted.getPublic()));
        ReflectionTestUtils.setField(verifier.getPublicKeysManager(),"expirationTimeMilliseconds",Long.MAX_VALUE);
    }
    GoogleIdToken.Payload payload() {
        var p=new GoogleIdToken.Payload();
        p.setSubject("stable-subject"); p.setEmail("user@gmail.com"); p.setEmailVerified(true);
        p.setAudience("our-client"); p.setIssuer("https://accounts.google.com");
        p.setIssuedAtTimeSeconds(Instant.now().getEpochSecond()-10); p.setExpirationTimeSeconds(Instant.now().getEpochSecond()+3600);
        return p;
    }
    String signed(GoogleIdToken.Payload p,KeyPair key) throws Exception {
        return JsonWebSignature.signUsingRsaSha256(key.getPrivate(),GsonFactory.getDefaultInstance(),
            new JsonWebSignature.Header().setAlgorithm("RS256").setType("JWT"),p);
    }
    @Test void acceptsBothGoogleIssuersAndUsesStableSubject() throws Exception {
        for(String issuer:List.of("accounts.google.com","https://accounts.google.com")) {
            var p=payload(); p.setIssuer(issuer);
            var identity=service.verify(signed(p,trusted));
            assertThat(identity.subject()).isEqualTo("stable-subject"); assertThat(identity.authoritative()).isTrue();
        }
    }
    @Test void rejectsWrongSignatureAudienceIssuerAndExpiredToken() throws Exception {
        assertThatThrownBy(()->service.verify(signed(payload(),untrusted))).isInstanceOf(IllegalArgumentException.class);
        var audience=payload(); audience.setAudience("malicious-client");
        var issuer=payload(); issuer.setIssuer("https://attacker.example");
        var expired=payload(); expired.setExpirationTimeSeconds(Instant.now().getEpochSecond()-1);
        for(var p:List.of(audience,issuer,expired))
            assertThatThrownBy(()->service.verify(signed(p,trusted))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void workspaceRequiresVerifiedEmailAndNonemptyHostedDomain() throws Exception {
        var p=payload(); p.setEmail("user@example.com");
        assertThat(service.verify(signed(p,trusted)).authoritative()).isFalse();
        p.setHostedDomain(""); assertThat(service.verify(signed(p,trusted)).authoritative()).isFalse();
        p.setHostedDomain("example.com"); assertThat(service.verify(signed(p,trusted)).authoritative()).isTrue();
        p.setEmailVerified(false); assertThatThrownBy(()->service.verify(signed(p,trusted))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void rejectsMissingSubjectAndMalformedTokens() throws Exception {
        var p=payload(); p.setSubject(null);
        assertThatThrownBy(()->service.verify(signed(p,trusted))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.verify("invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->service.verify(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
