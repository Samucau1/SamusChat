package com.chatapp.chatapp_backend.service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
@Service
public class AuthMailService {
    private final org.springframework.beans.factory.ObjectProvider<JavaMailSender> sender;
    private final String from;
    public AuthMailService(org.springframework.beans.factory.ObjectProvider<JavaMailSender> sender, @Value("${app.auth.mail-from:}") String from) {
        this.sender=sender; this.from=from;
    }
    public void recovery(String email, String code) {
        if (from.isBlank() || sender.getIfAvailable()==null) throw new IllegalStateException("Envio de email indisponivel");
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(from); message.setTo(email); message.setSubject("SamusChat - recuperar senha");
        message.setText("Seu codigo de recuperacao: " + code + "\nValido por 10 minutos. Nao compartilhe este codigo. Se nao solicitou, ignore este email.");
        try { sender.getObject().send(message); }
        catch (org.springframework.mail.MailException e) { throw new IllegalStateException("Envio de email indisponivel"); }
    }
}
