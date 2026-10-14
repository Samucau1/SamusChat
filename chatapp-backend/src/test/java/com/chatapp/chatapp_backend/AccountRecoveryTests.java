package com.chatapp.chatapp_backend;
import com.chatapp.chatapp_backend.dto.*;
import com.chatapp.chatapp_backend.entity.*;
import com.chatapp.chatapp_backend.repository.*;
import com.chatapp.chatapp_backend.service.*;
import com.chatapp.chatapp_backend.security.JwtUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(properties="app.auth.mail-from=no-reply@samuschat.test")
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
class AccountRecoveryTests {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    @Autowired PasswordRecoveryService recovery;
    @Autowired AuthService auth;
    @Autowired GoogleAuthService google;
    @Autowired UserRepository users;
    @Autowired PasswordRecoveryRepository recoveries;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtUtil jwt;
    @MockitoBean JavaMailSender mail;
    @MockitoBean GoogleTokenVerifier verifier;
    String email;
    @BeforeEach void createAccount() {
        email="test-"+UUID.randomUUID()+"@gmail.com";
        User u=new User(); u.setEmail(email); u.setUsername("test-"+UUID.randomUUID()); u.setPassword(passwords.encode("OldTestPassword1")); users.save(u);
    }
    String sentCode() {
        recovery.request(email);
        var message=ArgumentCaptor.forClass(SimpleMailMessage.class); verify(mail).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(email);
        String code=message.getValue().getText().split(": ")[1].substring(0,4);
        assertThat(code).matches("[0-9]{4}");
        assertThat(recoveries.findById(email).orElseThrow().getCodeHash()).isNotEqualTo(code);
        return code;
    }
    LoginRequest login(String password) { LoginRequest r=new LoginRequest(); r.setEmail(email); r.setPassword(password); return r; }
    @Test void emailCodeValidationPasswordChangeAndOldSessionRevocation() {
        String oldSession=auth.login(login("OldTestPassword1"));
        String code=sentCode(); String token=recovery.verify(email,code);
        assertThatThrownBy(()->recovery.verify(email,code)).isInstanceOf(IllegalArgumentException.class);
        recovery.reset(email,token,"NewTestPassword2");
        assertThatThrownBy(()->auth.login(login("OldTestPassword1"))).hasMessage("Credenciais invalidas");
        assertThat(jwt.isTokenValid(oldSession)).isFalse();
        assertThat(jwt.isTokenValid(auth.login(login("NewTestPassword2")))).isTrue();
        assertThatThrownBy(()->recovery.reset(email,token,"OtherPassword3")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void publicApiCompletesRecoveryAndDoesNotReturnCode() throws Exception {
        var response=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/password/request")
            .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("email",email))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        assertThat(response.getResponse().getContentAsString()).doesNotContain("codeHash", "resetToken");
        var message=ArgumentCaptor.forClass(SimpleMailMessage.class); verify(mail).send(message.capture());
        String code=message.getValue().getText().split(": ")[1].substring(0,4);
        var verified=mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/password/verify")
            .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("email",email,"code",code))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        String token=json.readTree(verified.getResponse().getContentAsString()).path("data").path("resetToken").asText();
        assertThat(token).hasSize(43);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/password/reset")
            .contentType("application/json").content(json.writeValueAsString(java.util.Map.of("email",email,"resetToken",token,"password","NewTestPassword2"))))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(jwt.isTokenValid(auth.login(login("NewTestPassword2")))).isTrue();
    }
    @Test void expiredAuthorizationAndResendInvalidatePreviousSecrets() {
        String code=sentCode(); String token=recovery.verify(email,code);
        var r=recoveries.findById(email).orElseThrow(); r.setExpiresAt(Instant.now().minusSeconds(1));
        r.setSentAt(Instant.now().minusSeconds(601)); recoveries.save(r);
        assertThatThrownBy(()->recovery.reset(email,token,"NewPassword2")).isInstanceOf(IllegalArgumentException.class);
        recovery.request(email);
        assertThat(recoveries.findById(email).orElseThrow().getResetHash()).isNull();
        assertThatThrownBy(()->recovery.reset(email,token,"NewPassword2")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void fiveFailedAttemptsPersistAndBlockCorrectCode() {
        String code=sentCode(); String wrong=code.equals("0000")?"0001":"0000";
        for(int i=0;i<5;i++) assertThatThrownBy(()->recovery.verify(email,wrong)).isInstanceOf(IllegalArgumentException.class);
        assertThat(recoveries.findById(email).orElseThrow().getAttempts()).isEqualTo(5);
        assertThatThrownBy(()->recovery.verify(email,code)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void expiredCodeAndUnauthorizedResetAreRejected() {
        String code=sentCode(); var r=recoveries.findById(email).orElseThrow(); r.setExpiresAt(Instant.now().minusSeconds(1)); recoveries.save(r);
        assertThatThrownBy(()->recovery.verify(email,code)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->recovery.reset(email,"fake","NewPassword1")).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void resendCooldownAndUnknownAccountDoNotSendMail() {
        sentCode(); recovery.request(email); verify(mail,times(1)).send(any(SimpleMailMessage.class));
        recovery.request("missing-"+UUID.randomUUID()+"@gmail.com"); verifyNoMoreInteractions(mail);
    }
    @Test void smtpFailureDoesNotLeaveAnActiveCode() {
        doThrow(new org.springframework.mail.MailSendException("offline")).when(mail).send(any(SimpleMailMessage.class));
        assertThatThrownBy(()->recovery.request(email)).hasMessage("Envio de email indisponivel");
        assertThat(recoveries.findById(email)).isEmpty();
    }
    @Test void googleLinksExistingAccountWithoutChangingPassword() {
        var before=users.findByEmail(email).orElseThrow();
        when(verifier.verify("valid")).thenReturn(new GoogleTokenVerifier.Identity("subject-"+email,email,true));
        assertThat(jwt.isTokenValid(google.login("valid",null))).isTrue();
        var after=users.findByEmail(email).orElseThrow();
        assertThat(after.getId()).isEqualTo(before.getId()); assertThat(after.getPassword()).isEqualTo(before.getPassword());
    }
    @Test void thirdPartyGoogleEmailNeedsCurrentPasswordBeforeLinking() {
        when(verifier.verify("valid")).thenReturn(new GoogleTokenVerifier.Identity("subject-"+email,email,false));
        assertThatThrownBy(()->google.login("valid",null)).hasMessageContaining("Confirme a senha");
        assertThat(users.findByEmail(email).orElseThrow().getGoogleSubject()).isNull();
        assertThat(jwt.isTokenValid(google.login("valid","OldTestPassword1"))).isTrue();
    }
    @Test void googleCreatesAccountAndRejectsInvalidTokens() {
        String newEmail="new-"+UUID.randomUUID()+"@gmail.com";
        when(verifier.verify("valid")).thenReturn(new GoogleTokenVerifier.Identity("subject-"+newEmail,newEmail,true));
        assertThat(jwt.isTokenValid(google.login("valid",null))).isTrue();
        assertThat(users.findByEmail(newEmail)).isPresent();
        when(verifier.verify("invalid")).thenThrow(new IllegalArgumentException("Token Google invalido"));
        assertThatThrownBy(()->google.login("invalid",null)).hasMessageContaining("Token Google invalido");
    }
}
